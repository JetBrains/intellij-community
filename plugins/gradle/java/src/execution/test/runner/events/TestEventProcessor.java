// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner.events;

import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemProgressEvent;
import com.intellij.openapi.externalSystem.model.task.event.TestOperationDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
public interface TestEventProcessor {

  void process(final @NotNull TestEventXmlView eventXml) throws TestEventXmlView.XmlParserException, NumberFormatException;

  void process(final @NotNull ExternalSystemProgressEvent<? extends TestOperationDescriptor> testEvent);
}
