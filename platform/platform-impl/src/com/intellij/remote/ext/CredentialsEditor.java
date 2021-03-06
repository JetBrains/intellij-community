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
package com.intellij.remote.ext;

import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.remote.RemoteSdkAdditionalData;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface CredentialsEditor<T> {
  /**
   * The user-visible name of the editor.
   * <p>
   * It will be used as the label of the option corresponding to this editor in
   * the outer form.
   *
   * @return the name of the editor
   */
  @NotNull
  @NlsContexts.Label String getName();

  JPanel getMainPanel();

  void onSelected();

  ValidationInfo validate();

  @NlsContexts.DialogMessage
  String validateFinal(@NotNull Supplier<? extends RemoteSdkAdditionalData<?>> supplier,
                       @NotNull Consumer<String> helpersPathUpdateCallback);

  void saveCredentials(T credentials);

  void init(T credentials);
}
