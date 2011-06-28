/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;

/**
 * @author Maxim.Medvedev
 */
public final class GroovyCommonClassNames {

  @NonNls public static final String GROOVY_OBJECT_SUPPORT = "groovy.lang.GroovyObjectSupport";
  @NonNls public static final String GROOVY_LANG_CLOSURE = "groovy.lang.Closure";
  @NonNls public static final String DEFAULT_BASE_CLASS_NAME = "groovy.lang.GroovyObject";
  @NonNls public static final String GROOVY_LANG_GSTRING = "groovy.lang.GString";
  @NonNls public static final String DEFAULT_GROOVY_METHODS = "org.codehaus.groovy.runtime.DefaultGroovyMethods";
  @NonNls public static final String GROOVY_LANG_SCRIPT = "groovy.lang.Script";
  @NonNls public static final String GROOVY_LANG_INT_RANGE = "groovy.lang.IntRange";
  @NonNls public static final String GROOVY_LANG_OBJECT_RANGE = "groovy.lang.ObjectRange";
  @NonNls public static final String GROOVY_LANG_DELEGATE = "groovy.lang.Delegate";
  @NonNls public static final String GROOVY_UTIL_CONFIG_OBJECT = "groovy.util.ConfigObject";
  @NonNls public static final String JAVA_UTIL_REGEX_PATTERN = "java.util.regex.Pattern";
  @NonNls public static final String JAVA_MATH_BIG_DECIMAL = "java.math.BigDecimal";
  @NonNls public static final String JAVA_MATH_BIG_INTEGER = "java.math.BigInteger";
  @NonNls public static final String ORG_CODEHAUS_GROOVY_RUNTIME_METHOD_CLOSURE = "org.codehaus.groovy.runtime.MethodClosure";
  @NonNls public static final String JAVA_UTIL_REGEX_MATCHER = "java.util.regex.Matcher";
  @NonNls public static final String GROOVY_TRANSFORM_FIELD = "groovy.transform.Field";
  @NonNls public static final String GROOVY_TRANSFORM_TUPLE_CONSTRUCTOR = "groovy.transform.TupleConstructor";
  @NonNls public static final String GROOVY_TRANSFORM_IMMUTABLE = "groovy.transform.Immutable";
  @NonNls public static final String GROOVY_TRANSFORM_CANONICAL = "groovy.transform.Canonical";
  @NonNls public static final String GROOVY_LANG_REFERENCE = "groovy.lang.Reference";
  @NonNls public static final String JAVA_UTIL_LINKED_HASH_MAP = "java.util.LinkedHashMap";


  private GroovyCommonClassNames() {
  }
}
