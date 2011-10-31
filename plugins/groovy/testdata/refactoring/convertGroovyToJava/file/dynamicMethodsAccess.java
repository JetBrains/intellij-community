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
public class Abc extends groovy.lang.GroovyObjectSupport implements groovy.lang.GroovyObject {
public java.lang.Object foo() {
invokeMethod("bar", new java.lang.Object[]{2});
java.util.Map<java.lang.String, java.lang.Integer> map = new java.util.Map<java.lang.String, java.lang.Integer>(1);
map.put("s", 4);
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(Abc.this, invokeMethod("bar", new java.lang.Object[]{map, 3}));
java.lang.String s = "a";
org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "bar", new java.lang.Object[]{4});
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(Abc.this, org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "bar", new java.lang.Object[]{5}));
return org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "anme", new java.util.ArrayList<java.lang.Object>());
}

}
