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
package com.intellij.ui.popup;

import com.intellij.openapi.actionSystem.Presentation;

/**
 * Marker interface for an action that wants to keep its popup opened if performed.
 *
 * @deprecated Replace with {@link Presentation#setKeepPopupOnPerform} with
 * {@link com.intellij.openapi.actionSystem.KeepPopupOnPerform#IfPreferred} or
 * {@link com.intellij.openapi.actionSystem.KeepPopupOnPerform#Always} (if needed) instead.
 */
@Deprecated(forRemoval = true)
public interface KeepingPopupOpenAction {
}
