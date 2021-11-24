// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.minperf;

/**
 * A list of monotone increasing values.
 */
interface MonotoneList {
  int get(int i);

  long getPair(int i);
}
