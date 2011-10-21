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
package com.intellij.testAssistant;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class GotoTestRelatedProvider extends GotoRelatedProvider {
  @NotNull
  @Override
  public List<? extends GotoRelatedItem> getItems(@NotNull DataContext context) {
    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(context);
    final Project project = PlatformDataKeys.PROJECT.getData(context);
    if (file != null && project != null) {
      final String ext = file.getExtension();
      String name = file.getNameWithoutExtension();
      final String filename;
      if (ext != null && ext.length() > 0 && name.length() > 0) {
        if (name.endsWith("Test") && name.length() > 4) {
          filename = name.substring(0, name.length() - 4) + "." + ext;
        } else {
          filename = name + "Test." + ext;
        }
        final PsiFile[] files = FilenameIndex.getFilesByName(project, filename, GlobalSearchScope.allScope(project));
        if (files.length > 0) {
          List<GotoRelatedItem> items = new ArrayList<GotoRelatedItem>();
          for (PsiFile psiFile : files) {
            items.add(new GotoRelatedItem(psiFile));
          }
          return items;
        }
      }
    }
    return super.getItems(context);
  }
}
