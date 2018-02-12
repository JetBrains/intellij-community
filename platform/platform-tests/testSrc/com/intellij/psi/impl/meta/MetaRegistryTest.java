/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.psi.impl.meta;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
* @author peter
*/
public class MetaRegistryTest extends LightPlatformTestCase {
  public void testChangingMetaData() {
    final boolean[] flag = {false};
    MetaRegistry.addMetadataBinding(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        return flag[0];
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }, MyTrueMetaData.class, getTestRootDisposable());
    MetaRegistry.addMetadataBinding(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        return !flag[0];
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }, MyFalseMetaData.class, getTestRootDisposable());

    final XmlTag tag = ((XmlFile)LightPlatformTestCase.createFile("a.xml", "<a/>")).getDocument().getRootTag();
    UsefulTestCase.assertInstanceOf(tag.getMetaData(), MyFalseMetaData.class);
    flag[0] = true;
    new WriteCommandAction(LightPlatformTestCase.getProject()) {
      @Override
      protected void run(@NotNull Result result) {
        tag.setName("b");
      }
    }.execute();
    UsefulTestCase.assertInstanceOf(tag.getMetaData(), MyTrueMetaData.class);
  }

  public static class MyAbstractMetaData implements PsiMetaData {
    private PsiElement myDeclaration;

    @Override
    public PsiElement getDeclaration() {
      return myDeclaration;
    }

    @NotNull
    @Override
    public Object[] getDependences() {
      return new Object[]{myDeclaration};
    }

    @Override
    @NonNls
    public String getName() {
      return null;
    }

    @Override
    @NonNls
    public String getName(PsiElement context) {
      return null;
    }

    @Override
    public void init(PsiElement element) {
      myDeclaration = element;
    }

  }

  public static class MyTrueMetaData extends MyAbstractMetaData {}
  public static class MyFalseMetaData extends MyAbstractMetaData {}

}
