// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.events

import jetbrains.buildServer.messages.serviceMessages.ServiceMessage

fun ServiceMessage.getStartNodeInfo(
  name: String?) = StartNodeEventInfo(
  name = name,
  id = TreeNodeEvent.getNodeId(this),
  locationUrl = BaseStartedNodeEvent.getLocation(this),
  parentId = BaseStartedNodeEvent.getParentNodeId(this),
  metainfo = BaseStartedNodeEvent.getMetainfo(this))


class StartNodeEventInfo constructor(
  name: String?,
  id: String?,
  val parentId: String?,
  val locationUrl: String?,
  val metainfo: String?) : NodeEventInfo(name, id)