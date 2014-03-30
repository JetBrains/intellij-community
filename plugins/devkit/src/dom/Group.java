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

// Generated on Wed Nov 07 17:26:02 MSK 2007
// DTD/Schema  :    plugin.dtd

package org.jetbrains.idea.devkit.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;

import java.util.List;

/**
 * plugin.dtd:group interface.
 */
public interface Group extends Actions {

	/**
	 * Returns the value of the popup child.
	 * Attribute popup
	 * @return the value of the popup child.
	 */
	@NotNull
	GenericAttributeValue<Boolean> getPopup();

	/**
	 * Returns the value of the compact child.
	 * Attribute popup
	 * @return the value of the compact child.
	 */
	@NotNull
	GenericAttributeValue<Boolean> getCompact();


	/**
	 * Returns the value of the icon child.
	 * Attribute icon
	 * @return the value of the icon child.
	 */
	@NotNull
	GenericAttributeValue<String> getIcon();


	/**
	 * Returns the value of the description child.
	 * Attribute description
	 * @return the value of the description child.
	 */
	@NotNull
	GenericAttributeValue<String> getDescription();


	/**
	 * Returns the value of the class child.
	 * Attribute class
	 * @return the value of the class child.
	 */
	@NotNull
	@com.intellij.util.xml.Attribute ("class")
        @ExtendClass(value = "com.intellij.openapi.actionSystem.ActionGroup",
            instantiatable = true, allowAbstract = false, allowInterface = false)
        @Convert(PluginPsiClassConverter.class)
	GenericAttributeValue<PsiClass> getClazz();


	/**
	 * Returns the value of the text child.
	 * Attribute text
	 * @return the value of the text child.
	 */
	@NotNull
	GenericAttributeValue<String> getText();

 	/**
	 * Returns the value of the id child.
	 * Attribute id
	 * @return the value of the id child.
	 */
        @NotNull
        GenericAttributeValue<String> getId();

  	///**
	// * Returns the value of the overrides child.
	// * Attribute overrides
	// * @return the value of the overrides child.
	// */
	//@NotNull
	//GenericAttributeValue<Boolean> getOverrides();

	/**
	 * Returns the list of reference children.
	 * @return the list of reference children.
	 */
	@NotNull
	List<Reference> getReferences();
	/**
	 * Adds new child to the list of reference children.
	 * @return created child
	 */
	Reference addReference();


	/**
	 * Returns the value of the separator child.
	 * @return the value of the separator child.
	 */
	@NotNull
	List<GenericDomValue<String>> getSeparators();


	/**
	 * Returns the list of action children.
	 * @return the list of action children.
	 */
	@NotNull
	List<Action> getActions();
	/**
	 * Adds new child to the list of action children.
	 * @return created child
	 */
	Action addAction();


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


}
