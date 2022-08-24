package org.jetbrains.completion.full.line.local

import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection

@Tag("metadata")
data class MavenMetadata(
  @Property(surroundWithTag = false)
  val versioning: Versioning,
) {
  @Suppress("unused")
  constructor() : this(Versioning())
}

@Tag("versioning")
data class Versioning(
  @Tag
  val latest: String,
  @Tag
  val release: String,
  @XCollection(propertyElementName = "versions", elementName = "version", valueAttributeName = "")
  val versions: List<String>,
  @Tag
  val lastUpdated: Long,
) {
  @Suppress("unused")
  constructor() : this("", "", emptyList(), 0)
}
