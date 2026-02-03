package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.MultiMap;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.psi.PsiEnhancerCategory;
import org.jetbrains.plugins.groovy.dsl.toplevel.ContextFilter;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("rawtypes")
public class GroovyDslExecutor {
  public GroovyDslExecutor(GdslScriptBase script, String fileName) {
    myScript = script;
    myFileName = fileName;
  }

  public List<Pair<ContextFilter, Closure>> getEnhancers() {
    return myScript.getEnhancers();
  }

  public MultiMap getStaticInfo() {
    return myScript.getStaticInfo();
  }

  public CustomMembersHolder processVariants(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    if (!DefaultGroovyMethods.asBoolean(getEnhancers())) return CustomMembersHolder.EMPTY;

    List<CustomMembersHolder> holders = new ArrayList<>();
    for (Pair<ContextFilter, Closure> pair : getEnhancers()) {
      ProgressManager.checkCanceled();
      ctx.put(DslPointcut.BOUND, null);
      if (pair.getFirst().isApplicable(descriptor, ctx)) {
        CustomMembersGenerator generator = new CustomMembersGenerator(descriptor, ctx.get(DslPointcut.BOUND));
        doRun(generator, pair.getSecond());
        List<CustomMembersHolder> membersHolder = generator.getMembersHolder();
        if (membersHolder != null) {
          holders.addAll(membersHolder);
        }
      }
    }

    return CustomMembersHolder.create(holders);
  }

  private void doRun(final CustomMembersGenerator generator, final Closure closure) {
    DefaultGroovyMethods.use(this, cats, new Closure<Object>(this, this) {
      public Object doCall(Object ignoredIt) {
        //noinspection unchecked
        return DefaultGroovyMethods.with(generator, closure);
      }
      @SuppressWarnings("unused")
      public Object doCall() {
        return doCall(null);
      }
    });
  }

  @Override
  public String toString() { return myFileName; }

  public static GroovyDslExecutor createAndRunExecutor(String text, String fileName) {
    CompilerConfiguration configuration = new CompilerConfiguration();
    configuration.setScriptBaseClass(GdslScriptBase.class.getName());
    GroovyShell shell = new GroovyShell(GroovyDslExecutor.class.getClassLoader(), configuration);
    GdslScriptBase script =
      DefaultGroovyMethods.asType(shell.parse(text, StringUtil.sanitizeJavaIdentifier(fileName)), GdslScriptBase.class);
    script.run();
    return new GroovyDslExecutor(script, fileName);
  }

  @SuppressWarnings("unused")
  public static List<Class> getCats() {
    return cats;
  }

  private static final List<Class> cats =
    DefaultGroovyMethods.collect(PsiEnhancerCategory.EP_NAME.getExtensions(),
                                 new Closure<Class>(null, null) {
                                   @SuppressWarnings("unused")
                                   public Class doCall(PsiEnhancerCategory it) { return it.getClass(); }
                                   @SuppressWarnings("unused")
                                   public Class doCall() {
                                     throw new UnsupportedOperationException();
                                   }
                                 });
  private final GdslScriptBase myScript;
  private final String myFileName;
}
