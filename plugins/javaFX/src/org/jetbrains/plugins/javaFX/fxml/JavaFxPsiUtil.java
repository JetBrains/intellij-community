/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * User: anna
 */
public class JavaFxPsiUtil {
  public static XmlProcessingInstruction createSingleImportInstruction(String qualifiedName, Project project) {
    final String importText = "<?import " + qualifiedName + "?>";
    final PsiElement child =
      PsiFileFactory.getInstance(project).createFileFromText("a.fxml", XMLLanguage.INSTANCE, importText).getFirstChild();
    return PsiTreeUtil.findChildOfType(child, XmlProcessingInstruction.class);
  }

  public static List<String> parseImports(XmlFile file) {
    return parseInstructions(file, "import");
  }

  public static List<String> parseInjectedLanguages(XmlFile file) {
    return parseInstructions(file, "language");
  }

  private static List<String> parseInstructions(XmlFile file, String instructionName) {
    List<String> definedImports = new ArrayList<String>();
    XmlDocument document = file.getDocument();
    if (document != null) {
      XmlProlog prolog = document.getProlog();

      final Collection<XmlProcessingInstruction>
        instructions = new ArrayList<XmlProcessingInstruction>(PsiTreeUtil.findChildrenOfType(prolog, XmlProcessingInstruction.class));
      for (Iterator<XmlProcessingInstruction> iterator = instructions.iterator(); iterator.hasNext(); ) {
        final XmlProcessingInstruction instruction = iterator.next();
        final ASTNode node = instruction.getNode();
        ASTNode xmlNameNode = node.findChildByType(XmlTokenType.XML_NAME);
        ASTNode importNode = node.findChildByType(XmlTokenType.XML_TAG_CHARACTERS);
        if (xmlNameNode == null || !instructionName.equals(xmlNameNode.getText()) || importNode == null) {
          iterator.remove();
        } else {
          definedImports.add(importNode.getText());
        }
      }
    }
    return definedImports;
  }
}
