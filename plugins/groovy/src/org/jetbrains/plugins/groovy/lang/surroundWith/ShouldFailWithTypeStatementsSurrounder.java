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
package org.jetbrains.plugins.groovy.lang.surroundWith;

/**
 * Provides the shouldFail() { ... }  surround with. It follows a Template Method pattern. 
 * @author Hamlet D'Arcy
 * @since 03.02.2009
 */
public class ShouldFailWithTypeStatementsSurrounder extends GroovySimpleManyStatementsSurrounder {

  @Override
  protected String getReplacementTokens() {
    return "shouldFail(a){\n}";
  }

  @Override
  public String getTemplateDescription() {
    return "shouldFail () {...}";
  }
}
