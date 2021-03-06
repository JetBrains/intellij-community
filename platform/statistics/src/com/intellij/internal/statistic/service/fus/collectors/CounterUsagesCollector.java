// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator;

/**
 * <p>Use it to create a collector which records user or internal IDE actions.</p>
 * <br/>
 * To implement a new collector:
 * <ol>
 *   <li>Inherit the class, implement {@link CounterUsagesCollector#getGroup()}, register all events you want to record and register collector in plugin.xml.
 *   See <i>fus-collectors.md</i> for more details.</li>
 *   <li>Implement custom validation rules if necessary. For more information see {@link IntellijSensitiveDataValidator}.</li>
 *   <li>If new group is implemented in a platform or a plugin built with IntelliJ Ultimate, YT issue will be created automatically</li>
 *   <li>Otherwise, create a YT issue in FUS project with group data scheme and descriptions to register it on the statistics metadata server</li>
 * </ol>
 * <br/>
 * To test collector:
 * <ol>
 *  <li>
 *    Open "Statistics Event Log" toolwindow.
 *  </li>
 *  <li>
 *    Add group to events test scheme with "Add Group to Events Test Scheme" action.<br/>
 *    {@link com.intellij.internal.statistic.actions.scheme.AddGroupToTestSchemeAction}
 *  </li>
 *  <li>
 *    Perform action to check that it's recorded in "Statistics Event Log" toolwindow.
 *  </li>
 * </ol>
 * <br/>
 * For more information see <i>fus-collectors.md</i>
 *
 * @see ApplicationUsagesCollector
 * @see ProjectUsagesCollector
 */
public abstract class CounterUsagesCollector extends FeatureUsagesCollector {
}
