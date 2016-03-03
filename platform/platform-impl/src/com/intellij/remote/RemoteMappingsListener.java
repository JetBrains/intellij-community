/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.remote;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * @author Irina.Chernushina on 2/4/2016.
 */
public interface RemoteMappingsListener {
  Topic<RemoteMappingsListener> REMOTE_MAPPINGS_CHANGED =
    new Topic<RemoteMappingsListener>("remotesdk.RemoteMappingsListener", RemoteMappingsListener.class);

  void mappingsChanged(@NotNull String prefix, @NotNull String serverId);
  void mappingsChanged();
}
