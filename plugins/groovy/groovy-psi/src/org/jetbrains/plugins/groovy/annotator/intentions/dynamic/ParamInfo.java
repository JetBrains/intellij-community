/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

/**
 * User: Dmitry.Krasilschikov
 * Date: 29.02.2008
 */
public class ParamInfo {
  public String name = null;
  public String type = null;

  public ParamInfo() {}

  public ParamInfo(String name, String type) {
    this.name = name;
    this.type = type;
  }

  public final int hashCode() {
    int hashCode = 0;
    if (name != null) {
      hashCode += name.hashCode();
    }
    if (type != null) {
      hashCode += type.hashCode();
    }
    return hashCode;
  }

  public String toString() {
    return "<" + name + "," + type + ">";
  }
}
