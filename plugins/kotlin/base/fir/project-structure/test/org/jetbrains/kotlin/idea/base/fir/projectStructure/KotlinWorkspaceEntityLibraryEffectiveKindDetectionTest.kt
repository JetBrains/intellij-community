// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.fir.projectStructure

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.LibraryTypeId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.workspaceModel.updateProjectModel
import org.jetbrains.kotlin.idea.base.platforms.KotlinCommonLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinNativeLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmJsLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.KotlinWasmWasiLibraryKind
import org.jetbrains.kotlin.idea.base.platforms.LibraryEffectiveKindProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

@TestApplication
class KotlinWorkspaceEntityLibraryEffectiveKindDetectionTest {
    companion object {
        val projectFixture: TestFixture<Project> = projectFixture()
    }

    @BeforeEach
    fun cleanUpLibraries() {
        runWriteAction {
            WorkspaceModel.getInstance(projectFixture.get()).updateProjectModel { storage ->
                storage.entities(LibraryEntity::class.java).forEach { storage.removeEntity(it) }
            }
        }
    }

    private fun <Kind> doTestWithProjectStructure(kind: Kind) where Kind : KotlinLibraryKind, Kind : PersistentLibraryKind<*> {
        val project = projectFixture.get()
        runWriteAction {
            WorkspaceModel.getInstance(project).updateProjectModel { storage ->
                storage.addEntity(
                    LibraryEntity(
                        tableId = LibraryTableId.ProjectLibraryTableId,
                        name = "testLibraryWithoutRoots",
                        roots = emptyList(),
                        entitySource = object : EntitySource {},
                    ) {
                        this.typeId = LibraryTypeId(kind.kindId)
                    }
                )
            }
        }

        val library = WorkspaceModel.getInstance(project).currentSnapshot.entities(LibraryEntity::class.java).singleOrNull()
        assertNotNull(library) { "The created library is missing in the workspace model" }
        val effectiveKind = project.getService(LibraryEffectiveKindProvider::class.java).getEffectiveKind(library)
        Assertions.assertEquals(kind, effectiveKind) { "Unexpected library kind: expected $kind, got $effectiveKind" }
    }

    @Test
    fun testKotlinJavaScriptLibraryKind() = doTestWithProjectStructure(KotlinJavaScriptLibraryKind)

    @Test
    fun testKotlinWasmJsLibraryKind() = doTestWithProjectStructure(KotlinWasmJsLibraryKind)

    @Test
    fun testKotlinWasmWasiLibraryKind() = doTestWithProjectStructure(KotlinWasmWasiLibraryKind)

    @Test
    fun testKotlinCommonLibraryKind() = doTestWithProjectStructure(KotlinCommonLibraryKind)

    @Test
    fun testKotlinNativeLibraryKind() = doTestWithProjectStructure(KotlinNativeLibraryKind)
}
