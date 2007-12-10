/*
 * Copyright 2006-2007 Bas Leijdekkers
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
package com.siyeh.ipp.interfacetoclass;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ConvertInterfaceToClassIntention extends Intention {

	private static void changeInterfaceToClass(PsiClass anInterface)
			throws IncorrectOperationException {
		final PsiIdentifier nameIdentifier = anInterface.getNameIdentifier();
		assert nameIdentifier != null;
		final PsiElement whiteSpace = nameIdentifier.getPrevSibling();
		assert whiteSpace != null;
		final PsiElement interfaceToken = whiteSpace.getPrevSibling();
		assert interfaceToken != null;
		final PsiKeyword interfaceKeyword =
                (PsiKeyword)interfaceToken.getOriginalElement();
		final PsiManager manager = anInterface.getManager();
          final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
		final PsiKeyword classKeyword = factory.createKeyword("class");
		interfaceKeyword.replace(classKeyword);

		final PsiModifierList classModifierList = anInterface.getModifierList();
        if (classModifierList == null) {
            return;
        }
        classModifierList.setModifierProperty(PsiModifier.ABSTRACT, true);
		final PsiElement parent = anInterface.getParent();
		if (parent instanceof PsiClass) {
			classModifierList.setModifierProperty(PsiModifier.STATIC, true);
		}
		final PsiMethod[] methods = anInterface.getMethods();
		for (final PsiMethod method : methods) {
			final PsiModifierList modifierList = method.getModifierList();
			modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
			modifierList.setModifierProperty(PsiModifier.ABSTRACT, true);
		}
		final PsiField[] fields = anInterface.getFields();
		for (final PsiField field : fields) {
			final PsiModifierList modifierList = field.getModifierList();
            if (modifierList != null) {
                modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
                modifierList.setModifierProperty(PsiModifier.STATIC, true);
                modifierList.setModifierProperty(PsiModifier.FINAL, true);
            }
        }
	}

	protected void processIntention(@NotNull PsiElement element)
			throws IncorrectOperationException {
		final PsiClass anInterface = (PsiClass)element.getParent();
        final boolean succes = moveSubClassImplementsToExtends(anInterface);
        if (!succes) {
            return;
        }
        changeInterfaceToClass(anInterface);
		moveExtendsToImplements(anInterface);
	}

	@NotNull
	protected PsiElementPredicate getElementPredicate() {
		return new ConvertInterfaceToClassPredicate();
	}

	private static void moveExtendsToImplements(PsiClass anInterface)
			throws IncorrectOperationException {
		final PsiReferenceList extendsList = anInterface.getExtendsList();
		final PsiReferenceList implementsList = anInterface.getImplementsList();
		assert extendsList != null;
		final PsiJavaCodeReferenceElement[] referenceElements =
				extendsList.getReferenceElements();
		for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
			assert implementsList != null;
			implementsList.add(referenceElement);
			referenceElement.delete();
		}
	}

	private static boolean moveSubClassImplementsToExtends(
            PsiClass oldInterface) throws IncorrectOperationException {
		final PsiManager psiManager = oldInterface.getManager();
		final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
          final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
		final PsiJavaCodeReferenceElement oldInterfaceReference =
				elementFactory.createClassReferenceElement(oldInterface);
		final SearchScope searchScope = oldInterface.getUseScope();
          final Collection<PsiClass> inheritors = ClassInheritorsSearch.search(oldInterface, searchScope, false).findAll();
        final Project project = oldInterface.getProject();
        final boolean succes =
                CommonRefactoringUtil.checkReadOnlyStatusRecursively(
                        project, inheritors, false);
        if (!succes) {
            return false;
        }
        for (PsiClass inheritor : inheritors) {
			final PsiReferenceList implementsList =
                    inheritor.getImplementsList();
			final PsiReferenceList extendsList = inheritor.getExtendsList();
			if (implementsList != null) {
				moveReference(implementsList, extendsList,
                              oldInterfaceReference);
			}
		}
        return true;
    }

	private static void moveReference(
            @NotNull PsiReferenceList source,
            @Nullable PsiReferenceList target,
            @NotNull PsiJavaCodeReferenceElement reference)
			throws IncorrectOperationException {
		final PsiJavaCodeReferenceElement[] implementsReferences =
				source.getReferenceElements();
		final String fqName = reference.getQualifiedName();
		for (PsiJavaCodeReferenceElement implementsReference :
                implementsReferences) {
			final String implementsReferenceFqName =
                    implementsReference.getQualifiedName();
			if (fqName.equals(implementsReferenceFqName)) {
				if (target != null) {
					target.add(implementsReference);
				}
				implementsReference.delete();
			}
		}
	}
}