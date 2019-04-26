// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

/**
 * These factors are calculated for user's commit and the candidate file that is not included in this commit
 */
internal enum class Factor {
  // maximal 'f(file)' for all files from current commit
  // where 'f' is count of commits where 'file' and candidate file were changed together
  MAX_INTERSECTION,

  // sum of 'f(file)' for all files from current commit
  // where 'f' is count of commits 'file' and candidate file were changed together
  SUM_INTERSECTION,

  // minimal distance between the current commit and the commit
  // where there was a file from the current commit and candidate file
  MIN_DISTANCE_TIME,

  // maximal distance between the current commit and the commit
  // where there was a file from the current commit and candidate file
  MAX_DISTANCE_TIME,

  // average distance between the current commit and the commit
  // where there was a file from the current commit and candidate file
  AVG_DISTANCE_TIME,

  // file's count of current commit
  COMMIT_SIZE,

  // size of maximal subset of files from current commit
  // which were changed together with candidate file
  MAX_COUNT,

  // size of minimal subset of files from current commit
  // which was in one commit with commits of candidate file
  MIN_COUNT,

  // 1.0 if author of current commit committed candidate file
  // 0.0 otherwise
  AUTHOR_COMMITTED_THE_FILE,

  // maximal common full path prefix length
  // between files from current commit and candidate file
  MAX_PREFIX_PATH,

  // maximal common name prefix length
  // between files from current commit and candidate file
  MAX_PREFIX_FILE_NAME
}