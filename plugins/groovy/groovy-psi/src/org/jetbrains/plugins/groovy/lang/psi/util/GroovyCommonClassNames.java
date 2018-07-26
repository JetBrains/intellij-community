// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util;

import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.newLinkedHashSet;
import static java.util.Collections.unmodifiableSet;

/**
 * @author Maxim.Medvedev
 */
public interface GroovyCommonClassNames {

  String GROOVY_OBJECT_SUPPORT = "groovy.lang.GroovyObjectSupport";
  String GROOVY_OBJECT = "groovy.lang.GroovyObject";
  String GROOVY_LANG_CLOSURE = "groovy.lang.Closure";
  String GROOVY_LANG_GSTRING = "groovy.lang.GString";
  String DEFAULT_GROOVY_METHODS = "org.codehaus.groovy.runtime.DefaultGroovyMethods";
  String GROOVY_LANG_SCRIPT = "groovy.lang.Script";
  String GROOVY_LANG_RANGE = "groovy.lang.Range";
  String GROOVY_LANG_INT_RANGE = "groovy.lang.IntRange";
  String GROOVY_LANG_OBJECT_RANGE = "groovy.lang.ObjectRange";
  String GROOVY_LANG_DELEGATE = "groovy.lang.Delegate";
  String GROOVY_UTIL_CONFIG_OBJECT = "groovy.util.ConfigObject";
  String JAVA_UTIL_REGEX_PATTERN = "java.util.regex.Pattern";
  String JAVA_MATH_BIG_DECIMAL = "java.math.BigDecimal";
  String JAVA_MATH_BIG_INTEGER = "java.math.BigInteger";
  String ORG_CODEHAUS_GROOVY_RUNTIME_METHOD_CLOSURE = "org.codehaus.groovy.runtime.MethodClosure";
  String JAVA_UTIL_REGEX_MATCHER = "java.util.regex.Matcher";
  String GROOVY_TRANSFORM_FIELD = "groovy.transform.Field";
  String GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR = "groovy.transform.TupleConstructor";
  String GROOVY_TRANSFORM_IMMUTABLE = "groovy.transform.Immutable";
  String GROOVY_TRANSFORM_CANONICAL = "groovy.transform.Canonical";
  String GROOVY_LANG_REFERENCE = "groovy.lang.Reference";
  String JAVA_UTIL_LINKED_HASH_MAP = "java.util.LinkedHashMap";
  String GROOVY_TRANSFORM_AUTO_EXTERNALIZE = "groovy.transform.AutoExternalize";
  String GROOVY_LANG_CATEGORY = "groovy.lang.Category";
  String GROOVY_LANG_MIXIN = "groovy.lang.Mixin";
  String GROOVY_UTIL_TEST_CASE = "groovy.util.GroovyTestCase";
  String GROOVY_LANG_SINGLETON = "groovy.lang.Singleton";
  String GROOVY_TRANSFORM_COMPILE_STATIC = "groovy.transform.CompileStatic";
  String GROOVY_TRANSFORM_TYPE_CHECKED = "groovy.transform.TypeChecked";
  String GROOVY_TRANSFORM_TYPE_CHECKING_MODE = "groovy.transform.TypeCheckingMode";
  String GROOVY_TRANSFORM_INHERIT_CONSTRUCTORS = "groovy.transform.InheritConstructors";
  String GROOVY_TRANSFORM_AUTOIMPLEMENT = "groovy.transform.AutoImplement";
  String GROOVY_LANG_IMMUTABLE = "groovy.lang.Immutable";
  String GROOVY_LANG_META_CLASS = "groovy.lang.MetaClass";
  String GROOVY_LANG_GROOVY_CALLABLE = "groovy.lang.GroovyCallable";
  String GROOVY_TRANSFORM_ANNOTATION_COLLECTOR = "groovy.transform.AnnotationCollector";
  String GROOVY_LANG_NEWIFY = "groovy.lang.Newify";
  String GROOVY_LANG_DELEGATES_TO = "groovy.lang.DelegatesTo";
  String GROOVY_LANG_DELEGATES_TO_TARGET = "groovy.lang.DelegatesTo.Target";
  String GROOVY_TRANSFORM_COMPILE_DYNAMIC = "groovy.transform.CompileDynamic";
  String GROOVY_TRANSFORM_STC_CLOSURE_PARAMS = "groovy.transform.stc.ClosureParams";
  String GROOVY_TRANSFORM_BASE_SCRIPT = "groovy.transform.BaseScript";
  String GROOVY_TRAIT = "groovy.transform.Trait";
  String GROOVY_TRAIT_IMPLEMENTED = "org.codehaus.groovy.transform.trait.Traits.Implemented";

  Set<String> DEFAULT_INSTANCE_EXTENSIONS = unmodifiableSet(newLinkedHashSet(
    "org.codehaus.groovy.runtime.DateGroovyMethods",
    "org.codehaus.groovy.runtime.DefaultGroovyMethods",
    "org.codehaus.groovy.runtime.EncodingGroovyMethods",
    "org.codehaus.groovy.runtime.IOGroovyMethods",
    "org.codehaus.groovy.runtime.ProcessGroovyMethods",
    "org.codehaus.groovy.runtime.ResourceGroovyMethods",
    "org.codehaus.groovy.runtime.SocketGroovyMethods",
    "org.codehaus.groovy.runtime.SqlGroovyMethods",
    "org.codehaus.groovy.runtime.StringGroovyMethods",
    "org.codehaus.groovy.runtime.SwingGroovyMethods",
    "org.codehaus.groovy.runtime.XmlGroovyMethods",
    "org.codehaus.groovy.vmplugin.v5.PluginDefaultGroovyMethods",
    "org.codehaus.groovy.runtime.dgmimpl.NumberNumberPlus",
    "org.codehaus.groovy.runtime.dgmimpl.NumberNumberMultiply",
    "org.codehaus.groovy.runtime.dgmimpl.NumberNumberMinus",
    "org.codehaus.groovy.runtime.dgmimpl.NumberNumberDiv"
  ));

  Set<String> DEFAULT_STATIC_EXTENSIONS = unmodifiableSet(newLinkedHashSet(
    "org.codehaus.groovy.runtime.DefaultGroovyStaticMethods"
  ));
}