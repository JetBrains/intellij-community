/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.misc.PsiElementSetSpinAllocator;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class AntElementNameReference extends AntGenericReference {

  public AntElementNameReference(final AntStructuredElement element) {
    super(element);
  }

  public AntElementNameReference(final AntStructuredElement element, final XmlAttribute attr) {
    super(element, attr);
  }

  public AntStructuredElement getElement() {
    return (AntStructuredElement)super.getElement();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final AntStructuredElement element = getElement();
    final AntTypeDefinition typeDef = element.getTypeDefinition();
    if (typeDef == null) return element;

    if (!(element instanceof AntTask)) {
      final AntStructuredElement defElement = (AntStructuredElement)typeDef.getDefiningElement();
      if (defElement != null && (defElement instanceof AntPresetDef || ((defElement.getParent() instanceof AntMacroDef || defElement.getParent() instanceof AntScriptDef) && "element".equals(defElement.getSourceElement().getName())))) {
        // renaming macrodef's or scriptdef's nested element
        element.getSourceElement().setName(newElementName);
      }
    }
    else {
      AntTask task = (AntTask)element;
      if (task.isMacroDefined() || task.isScriptDefined() || task.isPresetDefined() || task.isTypeDefined()) {
        final XmlAttribute attr = getAttribute();
        if (attr == null) {
          // renaming macrodef, presetdef or typedef/taskdef itself
          task.getSourceElement().setName(newElementName);
        }
        else {
          attr.setName(newElementName);
        }
      }
    }

    return element;
  }

  @Nullable
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (element instanceof AntStructuredElement) {
      return handleElementRename(((AntStructuredElement)element).getName());
    }
    return getElement();
  }

  public PsiElement resolveInner() {
    final AntStructuredElement element = getElement();
    final AntTypeDefinition elementDef = element.getTypeDefinition();
    if (elementDef != null) {
      final PsiElement definingElement = elementDef.getDefiningElement();
      if (element.isPresetDefined() || element.isTypeDefined()) {
        return definingElement;
      }
      if (!(element instanceof AntTask)) {
        return (definingElement == null) ? findClass(elementDef, element) : definingElement;
      }
      AntTask task = (AntTask)element;
      if (task.isMacroDefined() || task.isScriptDefined()) {
        final XmlAttribute attr = getAttribute();
        if (definingElement != null && attr != null) {
          final String attribName = attr.getName();
          for (PsiElement child : definingElement.getChildren()) {
            if (child instanceof AntStructuredElement && attribName.equals(((AntStructuredElement)child).getName())) {
              return child;
            }
          }
        }
        return definingElement;
      }
      return findClass(elementDef, element);
    }
    return null;
  }

  public boolean shouldBeSkippedByAnnotator() {
    return true;
  }

  @NotNull
  public Object[] getVariants() {
    if (getAttribute() != null) {
      return EMPTY_ARRAY; // scriptdef or mactodef params will be handled by XML implementation
    }
    final AntStructuredElement parent = (AntStructuredElement)getElement().getAntParent();
    if (parent == null) {
      return EMPTY_ARRAY;
    }
    AntTypeDefinition def = parent.getTypeDefinition();
    if (def == null) {
      def = parent.getAntProject().getTypeDefinition();
      if (def == null) {
        return EMPTY_ARRAY;
      }
    }
    final AntFile antFile = parent.getAntFile();
    final Project project = antFile.getProject();
    final Set<PsiElement> ids = PsiElementSetSpinAllocator.alloc();
    try {
      for (final AntTypeId id : def.getNestedElements()) {
        ids.add(new AntElementCompletionWrapper(parent, id.getName() + " ", project, AntElementRole.TASK_ROLE));
      }
      if (def.isAllTaskContainer()) {
        new Object() {
          final Set<AntFile> processedFiles = new HashSet<AntFile>();
          final Set<AntTypeId> processedTypes = new HashSet<AntTypeId>();
          
          void walkFiles(AntFile file) {
            if (processedFiles.contains(file)) {
              return;  
            }
            processedFiles.add(file);

            for (final AntTypeDefinition _def : file.getBaseTypeDefinitions()) {
              if (_def.isTask()) {
                final AntTypeId typeId = _def.getTypeId();
                if (!processedTypes.contains(typeId)) {
                  processedTypes.add(typeId);
                  ids.add(new AntElementCompletionWrapper(parent, typeId.getName() + " ", project, AntElementRole.TASK_ROLE));
                }
              }
            }

            for (AntFile imported : file.getAntProject().getImportedFiles()) {
              walkFiles(imported);
            }
          }
        }.walkFiles(antFile);
      }
      return ArrayUtil.toObjectArray(ids);
    }
    finally {
      PsiElementSetSpinAllocator.dispose(ids);
    }
  }

  @Nullable
  private static PsiElement findClass(final AntTypeDefinition elementDef, final AntStructuredElement element) {
    final String clazz = elementDef.getClassName();
    return JavaPsiFacade.getInstance(element.getProject()).findClass(clazz, GlobalSearchScope.allScope(element.getProject()));
  }
}
