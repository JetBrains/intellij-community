/*
 * Copyright 2000-2016 Nikolay Chashnikov.
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
package org.nik.presentationAssistant

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAware

/**
 * @author nik
 */
class ShowActionDescriptionsToggleAction : ToggleAction("Descriptions of Actions"), DumbAware {
    override fun isSelected(e: AnActionEvent) = getPresentationAssistant().configuration.showActionDescriptions

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        getPresentationAssistant().setShowActionsDescriptions(state, e.project)
    }
}