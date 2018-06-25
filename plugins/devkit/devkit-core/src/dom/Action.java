/*
 * Copyright 2000-2018 JetBrains s.r.o.
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

package org.jetbrains.idea.devkit.dom;

import com.intellij.ide.presentation.Presentation;
import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.ActionOrGroupResolveConverter;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;

import java.util.List;

/**
 * plugin.dtd:action interface.
 */
@Presentation(typeName = "Action")
public interface Action extends ActionOrGroup {


	/**
	 * Returns the value of the class child.
	 * Attribute class
	 * @return the value of the class child.
	 */
	@NotNull
	@Attribute ("class")
	@Required
        @ExtendClass(value = "com.intellij.openapi.actionSystem.AnAction",
		allowNonPublic = true, allowAbstract = false, allowInterface = false)
        @Convert(PluginPsiClassConverter.class)
        GenericAttributeValue<PsiClass> getClazz();


	/**
	 * Returns the list of keyboard-shortcut children.
	 * @return the list of keyboard-shortcut children.
	 */
	@NotNull
	List<KeyboardShortcut> getKeyboardShortcuts();
	/**
	 * Adds new child to the list of keyboard-shortcut children.
	 * @return created child
	 */
	KeyboardShortcut addKeyboardShortcut();


	/**
	 * Returns the list of mouse-shortcut children.
	 * @return the list of mouse-shortcut children.
	 */
	@NotNull
	List<MouseShortcut> getMouseShortcuts();
	/**
	 * Adds new child to the list of mouse-shortcut children.
	 * @return created child
	 */
	MouseShortcut addMouseShortcut();

        /**
 	 * Returns the list of abbreviation children.
 	 * @return the list of abbreviation children.
 	 */
 	@NotNull
 	List<Abbreviation> getAbbreviations();

 	/**
 	 * Adds new child to the list of abbreviation children.
 	 * @return created child
 	 */
        Abbreviation addAbbreviation();

	/**
	 * Returns the list of add-to-group children.
	 * @return the list of add-to-group children.
	 */
	@NotNull
	List<AddToGroup> getAddToGroups();
	/**
	 * Adds new child to the list of add-to-group children.
	 * @return created child
	 */
	AddToGroup addAddToGroup();

        @NotNull
	@Convert(ActionOrGroupResolveConverter.OnlyActions.class)
        GenericAttributeValue<ActionOrGroup> getUseShortcutOf();

        @NotNull
        GenericAttributeValue<String> getKeymap();

 	@NotNull
 	GenericAttributeValue<String> getProjectType();
}
