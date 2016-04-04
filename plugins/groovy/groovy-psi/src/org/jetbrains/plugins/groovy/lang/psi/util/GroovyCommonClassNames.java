/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.util.containers.ContainerUtil;

import java.util.Collections;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public final class GroovyCommonClassNames {
  public static final String GROOVY_OBJECT_SUPPORT = "groovy.lang.GroovyObjectSupport";
  public static final String GROOVY_OBJECT = "groovy.lang.GroovyObject";
  public static final String GROOVY_LANG_CLOSURE = "groovy.lang.Closure";
  public static final String DEFAULT_BASE_CLASS_NAME = "groovy.lang.GroovyObject";
  public static final String GROOVY_LANG_GSTRING = "groovy.lang.GString";
  public static final String DEFAULT_GROOVY_METHODS = "org.codehaus.groovy.runtime.DefaultGroovyMethods";
  public static final String GROOVY_LANG_SCRIPT = "groovy.lang.Script";
  public static final String GROOVY_LANG_INT_RANGE = "groovy.lang.IntRange";
  public static final String GROOVY_LANG_OBJECT_RANGE = "groovy.lang.ObjectRange";
  public static final String GROOVY_LANG_DELEGATE = "groovy.lang.Delegate";
  public static final String GROOVY_UTIL_CONFIG_OBJECT = "groovy.util.ConfigObject";
  public static final String JAVA_UTIL_REGEX_PATTERN = "java.util.regex.Pattern";
  public static final String JAVA_MATH_BIG_DECIMAL = "java.math.BigDecimal";
  public static final String JAVA_MATH_BIG_INTEGER = "java.math.BigInteger";
  public static final String ORG_CODEHAUS_GROOVY_RUNTIME_METHOD_CLOSURE = "org.codehaus.groovy.runtime.MethodClosure";
  public static final String JAVA_UTIL_REGEX_MATCHER = "java.util.regex.Matcher";
  public static final String GROOVY_TRANSFORM_FIELD = "groovy.transform.Field";
  public static final String GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR = "groovy.transform.TupleConstructor";
  public static final String GROOVY_TRANSFORM_IMMUTABLE = "groovy.transform.Immutable";
  public static final String GROOVY_TRANSFORM_CANONICAL = "groovy.transform.Canonical";
  public static final String GROOVY_LANG_REFERENCE = "groovy.lang.Reference";
  public static final String JAVA_UTIL_LINKED_HASH_MAP = "java.util.LinkedHashMap";
  public static final String GROOVY_TRANSFORM_AUTO_EXTERNALIZE = "groovy.transform.AutoExternalize";
  public static final String GROOVY_TRANSFORM_AUTO_CLONE = "groovy.transform.AutoClone";
  public static final String GROOVY_LANG_CATEGORY = "groovy.lang.Category";
  public static final String GROOVY_LANG_MIXIN = "groovy.lang.Mixin";
  public static final String GROOVY_UTIL_TEST_CASE = "groovy.util.GroovyTestCase";
  public static final String GROOVY_LANG_SINGLETON = "groovy.lang.Singleton";
  public static final String GROOVY_TRANSFORM_COMPILE_STATIC = "groovy.transform.CompileStatic";
  public static final String GROOVY_TRANSFORM_TYPE_CHECKED = "groovy.transform.TypeChecked";
  public static final String GROOVY_TRANSFORM_TYPE_CHECKING_MODE = "groovy.transform.TypeCheckingMode";
  public static final String GROOVY_TRANSFORM_INHERIT_CONSTRUCTORS = "groovy.transform.InheritConstructors";
  public static final String GROOVY_LANG_IMMUTABLE = "groovy.lang.Immutable";
  public static final String GROOVY_LANG_META_CLASS = "groovy.lang.MetaClass";
  public static final String GROOVY_LANG_GROOVY_CALLABLE = "groovy.lang.GroovyCallable";
  public static final String GROOVY_TRANSFORM_ANNOTATION_COLLECTOR = "groovy.transform.AnnotationCollector";
  public static final String GROOVY_LANG_NEWIFY = "groovy.lang.Newify";
  public static final String GROOVY_LANG_DELEGATES_TO = "groovy.lang.DelegatesTo";
  public static final String GROOVY_LANG_DELEGATES_TO_TARGET = "groovy.lang.DelegatesTo.Target";
  public static final String GROOVY_TRANSFORM_COMPILE_DYNAMIC = "groovy.transform.CompileDynamic";
  public static final String GROOVY_TRANSFORM_STC_CLOSURE_PARAMS = "groovy.transform.stc.ClosureParams";
  public static final String GROOVY_TRANSFORM_BASE_SCRIPT = "groovy.transform.BaseScript";
  public static final String GROOVY_TRAIT = "groovy.transform.Trait";
  public static final String GROOVY_TRAIT_IMPLEMENTED = "org.codehaus.groovy.transform.trait.Traits.Implemented";

  public static final Set<String> GROOVY_EXTENSION_CLASSES = Collections.unmodifiableSet(ContainerUtil.newLinkedHashSet(
    "org.codehaus.groovy.runtime.DateGroovyMethods",
    "org.codehaus.groovy.runtime.DefaultGroovyMethods",
    "org.codehaus.groovy.runtime.DefaultGroovyStaticMethods",
    "org.codehaus.groovy.runtime.EncodingGroovyMethods",
    "org.codehaus.groovy.runtime.IOGroovyMethods",
    "org.codehaus.groovy.runtime.ProcessGroovyMethods",
    "org.codehaus.groovy.runtime.ResourceGroovyMethods",
    "org.codehaus.groovy.runtime.SocketGroovyMethods",
    "org.codehaus.groovy.runtime.SqlGroovyMethods",
    "org.codehaus.groovy.runtime.StringGroovyMethods",
    "org.codehaus.groovy.runtime.SwingGroovyMethods",
    "org.codehaus.groovy.runtime.XmlGroovyMethods",
    "org.codehaus.groovy.vmplugin.v5.PluginDefaultGroovyMethods"
  ));

  private GroovyCommonClassNames() { }
}