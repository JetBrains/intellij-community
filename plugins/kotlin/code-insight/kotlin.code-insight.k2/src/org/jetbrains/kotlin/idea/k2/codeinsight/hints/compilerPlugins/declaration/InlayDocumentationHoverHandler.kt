// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints.compilerPlugins.declaration

import com.intellij.codeInsight.hints.InlayPresentationFactory
import com.intellij.lang.documentation.ide.impl.DocumentationManager
import com.intellij.lang.documentation.psi.psiDocumentationTargets
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.backend.documentation.impl.documentationRequest
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

internal object InlayDocumentationHoverHandler {
    private val EDITOR_DOC_SESSION_KEY = Key.create<DocumentationSession>("InlayDocumentationHoverHandler.DocumentationSession")

    private data class DocumentationSession(
        val targetPointer: SmartPsiElementPointer<*>,
        val session: DocumentationManager.DocumentationOnHoverSession
    )

    /**
     * Creates a hover listener that shows documentation for the specified class when hovering.
     */
    fun createHoverListener(
        targetPointer: SmartPsiElementPointer<*>,
        editor: Editor,
        project: Project
    ): InlayPresentationFactory.HoverListener {
        return object : InlayPresentationFactory.HoverListener {
            override fun onHover(event: MouseEvent, translated: java.awt.Point) {
                handleHover(targetPointer, event, editor, project)
            }

            override fun onHoverFinished() {
                handleHoverFinished(editor)
            }
        }
    }

    private fun handleHover(
        targetPointer: SmartPsiElementPointer<*>,
        mouseEvent: MouseEvent,
        editor: Editor,
        project: Project
    ) {
        val existingSession = editor.getUserData(EDITOR_DOC_SESSION_KEY)

        if (existingSession != null) {
            if (existingSession.targetPointer == targetPointer) {
                // If we have an existing session for the same class, just notify it that we're back
                existingSession.session.mouseWithinSourceArea()
                return
            } else {
                if (!existingSession.session.tryFinishImmediately()) {
                    return
                }
                editor.putUserData(EDITOR_DOC_SESSION_KEY, null)
            }
        }

        project.service<CsHolder>().cs.launch(Dispatchers.Default) {
            showDocumentation(targetPointer, mouseEvent, editor, project)
        }
    }

    private fun handleHoverFinished(editor: Editor) {
        editor.getUserData(EDITOR_DOC_SESSION_KEY)?.session?.mouseOutsideOfSourceArea()
    }

    private suspend fun showDocumentation(
        targetPointer: SmartPsiElementPointer<*>,
        mouseEvent: MouseEvent,
        editor: Editor,
        project: Project
    ) {
        val requests = readAction {
            val psiElement = targetPointer.element ?: return@readAction emptyList()
            val targets = psiDocumentationTargets(psiElement, psiElement)
            targets.map { it.documentationRequest() }
        }

        if (requests.isEmpty()) return

        val documentationManager = DocumentationManager.getInstance(project)

        withContext(Dispatchers.EDT) {
            val editorComponent = editor.contentComponent
            val mousePosition = SwingUtilities.convertPoint(
                mouseEvent.component,
                mouseEvent.point,
                editorComponent,
            )
            val hoverArea = java.awt.Rectangle(mousePosition.x, mousePosition.y, /* width = */ 1, /* height = */ 1)

            val session = documentationManager.showDocumentationOnHoverAroundByRequests(
                requests = requests,
                project = project,
                component = editorComponent,
                areaWithinComponent = hoverArea,
                minHeight = 200,
                delay = 500
            ) {
                editor.putUserData(EDITOR_DOC_SESSION_KEY, null)
            }
            if (session != null) {
                editor.putUserData(EDITOR_DOC_SESSION_KEY, DocumentationSession(targetPointer, session))
            }
        }
    }

    @Service(Service.Level.PROJECT)
    private class CsHolder(val cs: CoroutineScope)
}
