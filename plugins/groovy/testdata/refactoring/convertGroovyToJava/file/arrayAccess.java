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
public class Foo extends groovy.lang.GroovyObjectSupport implements groovy.lang.GroovyObject {
public void putAt(java.lang.String s, java.lang.Integer x, java.lang.Object value) {
}

public java.lang.Object getAt(java.lang.String s, java.lang.Integer x) {
return new java.lang.Object();
}

}
public class arrayAccess extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new arrayAccess(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {
java.util.HashMap<java.lang.String, java.lang.String> map = new java.util.HashMap<java.lang.String, java.lang.String>();
print(putAt0(map, "1", "6"));
print(putAt0(map, 2, "7"));
org.codehaus.groovy.runtime.DefaultGroovyMethods.putAt(map, "6", 1);
print(map.get("1"));
print(org.codehaus.groovy.runtime.DefaultGroovyMethods.getAt(map, 2));
Foo foo = new Foo();
foo.putAt("a", 2, 4);
print(putAt1(foo, "a", 2, 4));
print(foo.getAt("b", 1));
print(org.codehaus.groovy.runtime.DefaultGroovyMethods.getAt(foo, "4"));
java.lang.Integer[] arr = new java.lang.Integer[]{1, 2, 3};
print(arr[1]);
return arr[1] = 3;

}

public arrayAccess(groovy.lang.Binding binding) {
super(binding);
}
public arrayAccess() {
super();
}
private static <K, V, Value extends V>Value putAt0(java.util.Map<K, V> propOwner, K key, Value value) {
org.codehaus.groovy.runtime.DefaultGroovyMethods.putAt(propOwner, key, value);
return value;
}
private static <Value>Value putAt1(Foo propOwner, java.lang.String s, java.lang.Integer x, Value value) {
propOwner.putAt(s, x, value);
return value;
}
}
