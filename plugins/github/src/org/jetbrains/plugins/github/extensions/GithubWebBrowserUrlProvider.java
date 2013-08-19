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
package org.jetbrains.plugins.github.extensions;

import com.intellij.ide.browsers.Url;
import com.intellij.ide.browsers.UrlImpl;
import com.intellij.ide.browsers.WebBrowserUrlProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.GithubOpenInBrowserAction;

/**
 * @author Aleksey Pivovarov
 */
public class GithubWebBrowserUrlProvider extends WebBrowserUrlProvider {
  @Nullable
  @Override
  public Url getUrl(@NotNull PsiElement element, @NotNull PsiFile psiFile, @NotNull VirtualFile virtualFile) throws BrowserException {
    String url = GithubOpenInBrowserAction.getGithubUrl(element.getProject(), virtualFile, null, true);
    if (url == null) {
      return null;
    }
    return new UrlImpl(url, "https", null, null, null);
  }

  @Nullable
  @Override
  public String getOpenInBrowserActionText(@NotNull PsiFile file) {
    return "Open on GitHub";
  }
}
