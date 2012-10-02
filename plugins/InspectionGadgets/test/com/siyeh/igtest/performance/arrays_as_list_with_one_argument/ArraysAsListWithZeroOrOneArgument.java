package com.siyeh.igtest.performance.arrays_as_list_with_one_argument;

import java.util.Arrays;

class ArraysAsListWithZeroOrOneArgument {{
  Arrays.asList();
  Arrays.asList("one");
  Arrays.asList("one", "two");
  Arrays.asList(new String[] {"asdf", "foo"});
}}