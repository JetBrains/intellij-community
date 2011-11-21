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
package com.intellij.openapi.vcs.checkout;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsKey;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/19/11
 * Time: 1:48 PM
 */
public interface VcsAwareCheckoutListener {
  ExtensionPointName<VcsAwareCheckoutListener> EP_NAME = ExtensionPointName.create("com.intellij.vcsAwareCheckoutListener");
  boolean processCheckedOutDirectory(final Project project, final File directory, final VcsKey vcsKey);
}
