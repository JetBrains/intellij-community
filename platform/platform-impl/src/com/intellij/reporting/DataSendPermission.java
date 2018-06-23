/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.reporting;


import com.intellij.openapi.extensions.ExtensionPointName;


/**
 * Currently we want to send completion related data from both EAP users and users with installed <a href="https://plugins.jetbrains.com/plugin/8117-completion-stats-collector">plugin</a>
 * In order to do that collecting is bundled into platform as a "Stats Collector" plugin, while being removed from "Completion Stats Collector" plugin.
 * This extension point allows non-bundled <a href="https://plugins.jetbrains.com/plugin/8117-completion-stats-collector">plugin</a> plugin to enable logging for non-EAP users.
 *
 * INTERNAL USE ONLY
 */
public interface DataSendPermission {
  boolean isDataSendAllowed();
  ExtensionPointName<DataSendPermission> EP_NAME = new ExtensionPointName<>("com.intellij.reporting.sendPermission");
}
