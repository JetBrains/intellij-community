// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.exceptons

/**
 * This exception should be thrown when we can not open the lesson
 * due to some reason that should not be logged as error
 */
class LessonPreparationException(message: String, cause: Throwable? = null)
  : IllegalStateException(message, cause)