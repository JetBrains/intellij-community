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
package com.jetbrains.reactiveidea.mapping

import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.usages.TextChunk
import com.intellij.usages.UsageViewPresentation
import com.jetbrains.reactivemodel.mapping.Mapping
import com.jetbrains.reactivemodel.mapping.Original
import com.jetbrains.reactivemodel.mapping.model.ModelBean
import com.jetbrains.reactivemodel.models.MapModel

@Mapping(javaClass<UsageViewPresentation>())
data class UsageViewPresentationBean(
    val tabText: String,
    val scopeText: String,
    val contextText: String,
    val usagesString: String?,
    val targetsNodeText: String,
    val nonCodeUsagesString: String,
    val usagesInGeneratedCodeString: String,
    val showReadOnlyStatusAsRed: Boolean,
    val showCancelButton: Boolean,
    val openInNewTab: Boolean,
    val codeUsages: Boolean,
    val usageTypeFilteringAvailable: Boolean,
    val usagesWord: String,
    val tabName: String?,
    val toolwindowTitle: String?,
    val dynamicCodeUsagesString: String?,
    val mergeDupLinesAvailable: Boolean) : ModelBean


@Mapping(javaClass<TextChunk>())
data class TextChunkBean(val text: String,
                         @Original("attributes") val attr: TextAttributesBean)



