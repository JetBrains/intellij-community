// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events;

import org.gradle.tooling.events.ScriptPluginIdentifier;
import org.jetbrains.annotations.ApiStatus;

import java.net.URI;

@ApiStatus.Internal
public final class InternalScriptPluginIdentifier extends AbstractInternalPluginIdentifier implements ScriptPluginIdentifier {
  private final URI uri;

  public InternalScriptPluginIdentifier(String displayName, URI uri) {
    super(displayName);
    this.uri = uri;
  }

  @Override
  public URI getUri() {
    return this.uri;
  }
}
