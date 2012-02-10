/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.facet;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 11, 2009
 * Time: 8:29:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class SimpleClassMapConstructor implements ClassMapConstructor {

  private SimpleClassMapConstructor() {
  }

  private static class SimpleClassMapConstructorHolder {
    private static final SimpleClassMapConstructor INSTANCE = new SimpleClassMapConstructor();
  }

  public static SimpleClassMapConstructor getInstance() {
    return SimpleClassMapConstructorHolder.INSTANCE;
  }

  @NotNull
  public String[] getTagNamesByClass(@NotNull final PsiClass c) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      public String[] compute() {
        String name = c.getName();
        if (name != null) {
          String qualifiedName = c.getQualifiedName();
          if (qualifiedName != null) {
            if (!isAndroidLibraryClass(qualifiedName)) {
              return new String[]{qualifiedName};
            }
            return new String[]{name, qualifiedName};
          }
          return new String[]{name};
        }
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
    });
  }

  protected static boolean isAndroidLibraryClass(@NotNull String qualifiedClassName) {
    String[] ar = qualifiedClassName.split("\\.");
    return ar.length < 0 || ar[0].equals("android");
  }
}
