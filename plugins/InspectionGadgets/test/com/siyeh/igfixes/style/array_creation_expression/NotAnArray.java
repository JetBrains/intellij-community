package com.siyeh.ipp.braces.array_creation;

import java.util.Map;

class NotAnArray {{
  Map<String, String> m = <caret>{};
}}