package org.jetbrains.plugins.feature.suggester.cache

import org.jetbrains.plugins.feature.suggester.changes.UserAnAction

class UserAnActionsCache(maxCacheSize: Int) : ChangedCache<UserAnAction>(maxCacheSize) {
}