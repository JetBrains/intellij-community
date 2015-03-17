package com.siyeh.igtest.performance.arrays_as_list_with_one_argument;

import java.util.Arrays;

class ArraysAsListWithZeroOrOneArgument {{
  Arrays.<warning descr="Call to 'asList' with zero arguments">asList</warning>();
  Arrays.<warning descr="Call to 'asList' with only one argument">asList</warning>("one");
  Arrays.asList("one", "two");
  Arrays.asList(new String[] {"asdf", "foo"});
}}