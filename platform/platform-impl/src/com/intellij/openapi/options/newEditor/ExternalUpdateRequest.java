// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.options.Configurable;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface ExternalUpdateRequest {
  @Topic.AppLevel
  Topic<ExternalUpdateRequest> EXTERNAL_UPDATE_REQUEST_TOPIC = new Topic<>(ExternalUpdateRequest.class, Topic.BroadcastDirection.NONE);

  void requestUpdate(Configurable configurable);
}
