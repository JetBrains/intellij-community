package com.intellij.jps.cache.hashing;

import java.io.File;

interface PathRelativizer {
  String relativize(File target);
}
