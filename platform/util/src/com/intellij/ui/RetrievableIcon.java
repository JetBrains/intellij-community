/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;


/**
 * This class is mostly for testing purposes: in case an icon is hidden behind a private or a restricted interface,
 * marking it as RetrievableIcon will help get the actual icon and perform checks.
 */
public interface RetrievableIcon extends Icon {
  @Nullable
  Icon retrieveIcon();
}