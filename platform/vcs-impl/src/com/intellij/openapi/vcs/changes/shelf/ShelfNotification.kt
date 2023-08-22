// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.xml.util.XmlStringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

open class ShelfNotification(groupDisplayId: @NonNls String,
                             title: @Nls String,
                             content: @Nls String,
                             type: NotificationType) : Notification(groupDisplayId, title, content, type)

class ShelfDeleteNotification(content: @Nls String) : ShelfNotification(VcsNotifier.STANDARD_NOTIFICATION.displayId,
                                                                           VcsBundle.message("shelve.deletion.title"),
                                                                           XmlStringUtil.wrapInHtml(content),
                                                                           NotificationType.INFORMATION)
