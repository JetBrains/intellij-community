package org.jetbrains.completion.full.line.local

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlChildrenName

@Serializable
@SerialName("metadata")
data class MavenMetadata(
    val versioning: Versioning,
)

@Serializable
data class Versioning(
    val latest: String,
    val release: String,
    @XmlChildrenName("version", "", "")
    val versions: List<String>,
    val lastUpdated: Long,
)