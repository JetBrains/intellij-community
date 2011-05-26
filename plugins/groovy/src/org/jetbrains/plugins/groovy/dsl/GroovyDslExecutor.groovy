package org.jetbrains.plugins.groovy.dsl

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PsiJavaPatterns
import com.intellij.util.ProcessingContext
import java.lang.reflect.Modifier
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider
import org.jetbrains.plugins.groovy.dsl.psi.PsiEnhancerCategory
import org.jetbrains.plugins.groovy.dsl.toplevel.CompositeContextFilter
import org.jetbrains.plugins.groovy.dsl.toplevel.Context
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.AnnotatedScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClassScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ClosureScope
import org.jetbrains.plugins.groovy.dsl.toplevel.scopes.ScriptScope

/**
 * @author ilyas
 */

public class GroovyDslExecutor {
  static final def cats = PsiEnhancerCategory.EP_NAME.extensions.collect { it.class }
  final List<Pair<ContextFilter, Closure>> enhancers = []

  private final String myFileName;
  static final String ideaVersion
  private boolean locked = false

  static {
    def major = ApplicationInfo.instance.majorVersion
    def minor = ApplicationInfo.instance.minorVersion
    def full = major + (minor ? ".$minor" : "")
    ideaVersion = full
  }

  public GroovyDslExecutor(String text, String fileName) {
    myFileName = fileName

    def shell = new GroovyShell()
    def script = shell.parse(text, fileName)

    def mc = new ExpandoMetaClass(script.class, false)

    mc.methodMissing = { String name, Object args -> return DslPointcut.UNKNOWN }

    def contribute = {cts, Closure toDo ->
      cts = handleImplicitBind(cts)

      if (cts instanceof DslPointcut) {
        assert cts.operatesOn(GroovyClassDescriptor) : "A non top-level pointcut passed to contributor"
        addClassEnhancer([new PointcutContextFilter(cts)], toDo)
        return
      }

      if (cts instanceof Map) {
        cts = new Context(cts)
      }
      if (!(cts instanceof List)) {
        assert cts instanceof Context: "The contributor() argument must be a context"
        cts = [cts]
      }
      def contexts = cts.findAll { it != null } as List
      if (contexts) {
        def filters = contexts.collect { return it.filter }
        addClassEnhancer(filters, toDo)
      }
    }
    mc.contributor = contribute
    mc.contribute = contribute

    mc.bind = { arg -> DslPointcut.bind(arg) }

    for (m in DslPointcut.class.declaredMethods) {
      if ((m.modifiers & Modifier.STATIC) && (m.modifiers & Modifier.PUBLIC)) {
        def name = m.name
        if (name != 'bind') {
          mc."$name" = { arg -> org.jetbrains.plugins.groovy.dsl.DslPointcut."$name"(handleImplicitBind(arg)) }
        }
      }
    }

    oldStylePrimitives(mc)

    mc.initialize()
    script.metaClass = mc
    script.run()

    locked = true
  }

  private def handleImplicitBind(arg) {
    if (arg instanceof Map && arg.size() == 1 && arg.keySet().iterator().next() instanceof String && arg.values().iterator().next() instanceof DslPointcut) {
      return DslPointcut.bind(arg)
    }
    return arg
  }

  private void oldStylePrimitives(MetaClass mc) {
    mc.supportsVersion = { String ver ->
      StringUtil.compareVersionNumbers(ideaVersion, ver) >= 0
    }

    /**
     * Context definition
     */
    mc.context = {Map args -> return new Context(args) }

    /**
     * Auxiliary methods for context definition
     */
    mc.closureScope = {Map args -> return new ClosureScope(args)}
    mc.scriptScope = {Map args -> return new ScriptScope(args)}
    mc.classScope = {Map args -> return new ClassScope(args)}

    /**
     * @since 10
     */
    mc.annotatedScope = {Map args -> return new AnnotatedScope(args)}

    mc.hasAnnotation = { String annoQName -> PsiJavaPatterns.psiModifierListOwner().withAnnotation(annoQName) }
    mc.hasField = { ElementPattern fieldCondition -> PsiJavaPatterns.psiClass().withField(true, PsiJavaPatterns.psiField().and(fieldCondition)) }
    mc.hasMethod = { ElementPattern methodCondition -> PsiJavaPatterns.psiClass().withMethod(true, PsiJavaPatterns.psiMethod().and(methodCondition)) }
  }

  def addClassEnhancer(List<ContextFilter> cts, Closure toDo) {
    assert !locked : 'Contributing to GDSL is only allowed at the top-level of the *.gdsl script'
    enhancers << Pair.create(CompositeContextFilter.compose(cts, false), toDo)
  }

  def processVariants(GroovyClassDescriptor descriptor, CustomMembersGenerator consumer, ProcessingContext ctx) {
    for (pair in enhancers) {
      if (pair.first.isApplicable(descriptor, ctx)) {
        Closure f = pair.second.clone()
        f.delegate = consumer
        consumer.metaClass = contributionDelegateMetaClass(ctx, consumer)
        f.resolveStrategy = Closure.DELEGATE_FIRST

        use(cats) {
          f.call()
        }
      }
    }
  }

  static final def memberProviders = GdslMembersProvider.EP_NAME.getExtensions()

  private ExpandoMetaClass contributionDelegateMetaClass(ProcessingContext ctx, CustomMembersGenerator consumer) {
    def mc = new ExpandoMetaClass(CustomMembersGenerator)
    mc.methodMissing = { String name, Object args ->
      final def newArgs = consumer.constructNewArgs(args)

      // Get other DSL methods from extensions
      for (d in memberProviders) {
        final def variants = d.metaClass.respondsTo(d, name, newArgs)
        if (variants.size() == 1) {
          return d.invokeMethod(name, newArgs)
        }
      }
      return null
    }

    def bound = ctx.get(DslPointcut.BOUND)
    bound.each { name, value -> mc."$name" = value }
    mc.initialize()
    return mc
  }

  def String toString() {
    return "${super.toString()}; file = $myFileName";
  }

}
