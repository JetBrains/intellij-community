// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline

import org.jetbrains.plugins.github.api.data.GHActor
import java.util.*

/*
type CrossReferencedEvent implements Node & UniformResourceLocatable {
    "Identifies the actor who performed the event."
    actor: Actor
    "Identifies the date and time when the object was created."
    createdAt: DateTime!
    id: ID!
    "Reference originated in a different repository."
    isCrossRepository: Boolean!
    "Identifies when the reference was made."
    referencedAt: DateTime!
    "The HTTP path for this pull request."
    resourcePath: URI!
    "Issue or pull request that made the reference."
    source: ReferencedSubject!
    "Issue or pull request to which the reference was made."
    target: ReferencedSubject!
    "The HTTP URL for this pull request."
    url: URI!
    "Checks if the target will be closed when the source is merged."
    willCloseTarget: Boolean!
}
 */
class GHPRCrossReferencedEvent(override val actor: GHActor?,
                               override val createdAt: Date,
                               val source: ReferencedSubject)
  : GHPRTimelineEvent.Complex {

  class ReferencedSubject(val title: String, val number: Long, val url: String)
}