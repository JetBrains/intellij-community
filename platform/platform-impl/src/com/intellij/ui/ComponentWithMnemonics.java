/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/**
 * Markup interface with two purposes:
 * <ol>
 * <li>To find components with mnemonics (root components) in JLayeredPane (balloons, etc..)</li>
 * <li>To mark visual 'scopes' inside IDE frame and prevent a collision between mnemonics and shortcuts</li>
 * </ol>
 * As for the second purpose:
 * <p>In terms of Swing 'scope' for mnemonic is whole RootPaneContainer like JFrame of JDialog but main IDE frame is too complex
 * and cannot be considered as 'single UI'. So there should be some user-friendly 'scopes' or 'contexts'.</p>
 * <p>For example, in focused editor global action with some alt+{char} shortcut is more preferable than the same alt+{char} mnemonic
 * presented inside tool window (and vise versa, when focus comes to the tool window namely mnemonic is expected to be more preferable).</p>
 *
 * @author Konstantin Bulenkov
 * @author Vassiliy.Kudryashov
 */
public interface ComponentWithMnemonics {
}
