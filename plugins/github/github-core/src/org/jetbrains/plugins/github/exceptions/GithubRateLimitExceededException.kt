/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.exceptions

import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * @author Aleksey Pivovarov
 */
open class GithubRateLimitExceededException @JvmOverloads constructor(
  message: String,
  /**
   * The time when the rate limit resets, or `null` if the server didn't report it.
   */
  val resetAt: Instant? = null,
) : IOException(message)

/**
 * GitHub rejected a request because of a secondary rate limit, such as too many requests per minute.
 *
 * See https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2026-03-10#about-secondary-rate-limits.
 *
 * @property retryAfter the time that GitHub asks to wait before the next request, or `null` if the server didn't report it
 */
class GithubSecondaryRateLimitExceededException(message: String, val retryAfter: Duration?) :
  GithubRateLimitExceededException(message, null)
