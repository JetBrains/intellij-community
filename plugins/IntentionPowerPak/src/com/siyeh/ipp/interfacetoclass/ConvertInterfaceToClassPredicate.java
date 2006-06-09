/*
 * Copyright 2006 Bas Leijdekkers
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

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.siyeh.ipp.base.PsiElementPredicate;

class ConvertInterfaceToClassPredicate implements PsiElementPredicate {

	public boolean satisfiedBy(PsiElement element) {
		final PsiElement parent = element.getParent();
		if (!(parent instanceof PsiClass)) {
			return false;
		}
		final PsiClass aClass = (PsiClass)parent;
		if (!aClass.isInterface() || aClass.isAnnotationType()) {
			return false;
		}
		final PsiJavaToken leftBrace = aClass.getLBrace();
		final int offsetInParent = element.getStartOffsetInParent();
		if (leftBrace == null || offsetInParent >= leftBrace.getStartOffsetInParent()) {
			return false;
		}
		if (!element.isWritable()) {
			return false;
		}
		final PsiManager manager = element.getManager();
		final PsiSearchHelper searchHelper = manager.getSearchHelper();
		final SearchScope useScope = aClass.getUseScope();
		final PsiClass[] inheritors = searchHelper.findInheritors(aClass, useScope, true);
		for (PsiClass inheritor : inheritors) {
			if (inheritor.isInterface()) {
				return false;
			}
		}
		return true;
	}
}
