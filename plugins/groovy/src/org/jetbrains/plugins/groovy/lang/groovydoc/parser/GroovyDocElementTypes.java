/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.groovydoc.parser;

import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.lang.GroovyDocLanguage;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.psi.tree.IFileElementType;

/**
 * @author ilyas
 */
public interface GroovyDocElementTypes extends GroovyDocTokenTypes{

  /**
   * GroovyDoc comment 
   */
  GroovyElementType GROOVY_DOC_COMMENT = new GroovyElementType("GroovyDocComment");

  // Filetype for Parser definition
  IFileElementType GROOVY_DOC_DUMMY_FILE = new IFileElementType(GroovyDocLanguage.LANGUAGE);

}
