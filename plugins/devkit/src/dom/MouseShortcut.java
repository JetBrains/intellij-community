/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import org.jetbrains.annotations.NotNull;

/**
 * plugin.dtd:mouse-shortcut interface.
 */
public interface MouseShortcut extends DomElement {

	/**
	 * Returns the value of the keymap child.
	 * Attribute {@code keymap}
	 * @return the value of the keymap child.
	 */
	@NotNull
	@Required
	GenericAttributeValue<String> getKeymap();

	/**
	 * Returns the value of the keystroke child.
	 * Attribute {@code keystroke}
	 * @return the value of the keystroke child.
	 */
	@NotNull
	@Required
        @NoSpellchecking
	GenericAttributeValue<String> getKeystroke();

        /**
         * Returns the value of the should current shortcut be removed or not.
         * Attribute {@code remove}
         * @return the value of the should current shortcut be removed or not.
         */
        @NotNull
        GenericAttributeValue<Boolean> getRemove();

        /**
         * Returns the value of the should all previous shortcuts be removed by that one or not.
         * Attribute {@code replace-all}
         * @return the value of the should all previous shortcuts be removed by that one or not.
         */
        @NotNull
        GenericAttributeValue<Boolean> getReplaceAll();
}
