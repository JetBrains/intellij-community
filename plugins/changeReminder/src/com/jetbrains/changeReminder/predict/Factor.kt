// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

enum class Factor {
  // maximal 'f(file)' for all files from current commit
  // where 'f' is count of commits where were 'file' and file to predict
  MAX_INTERSECTION,

  // sum of 'f(file)' for all files from current commit
  // where 'f' is count of commits where were 'file' and file to predict
  SUM_INTERSECTION,

  // minimal distance between the current commit and the commit
  // where there was a file from the current commit and file to predict
  MIN_DISTANCE_TIME,

  // maximal distance between the current commit and the commit
  // where there was a file from the current commit and file to predict
  MAX_DISTANCE_TIME,

  // average distance between the current commit and the commit
  // where there was a file from the current commit and file to predict
  AVG_DISTANCE_TIME,

  // file's count of current commit
  COMMIT_SIZE,

  // size of maximal subset of files from current commit
  // which was in one commit with commits of file to predict
  MAX_COUNT,

  // size of minimal subset of files from current commit
  // which was in one commit with commits of file to predict
  MIN_COUNT,

  // 1.0 if author of current commit committed file which we want to predict
  // 0.0 otherwise
  AUTHOR_COMMITTED_THE_FILE,

  // maximal 'f(file, file to predict)' for all files from current commit
  // where 'f' is length of maximal prefix of full file paths
  MAX_PREFIX_PATH,

  // maximal 'f(file, file to predict)' for all files from current commit
  // where 'f' is length of maximal prefix of file names
  MAX_PREFIX_FILE_NAME
}