package com.intellij.workspace.model.api

import com.intellij.workspaceModel.storage.PersistentEntityId

data class LibraryId(val name: String, val tableId: LibraryTableId) : PersistentEntityId<LibraryEntity> {
    override val presentableName: String
        get() = name
}

data class ModuleId(val name: String) : PersistentEntityId<ModuleEntity> {
    override val presentableName: String
        get() = name
}

data class FacetId(val name: String, val type: String, val parentId: ModuleId) : PersistentEntityId<FacetEntity> {
    override val presentableName: String
        get() = name
}

data class ArtifactId(val name: String) : PersistentEntityId<ArtifactEntity> {
    override val presentableName: String
        get() = name
}