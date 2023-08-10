package com.intellij.driver.model;

import com.intellij.driver.model.transport.PassByValue;

import java.io.Serializable;
import java.util.HashMap;

public final class StringTable extends HashMap<Integer, HashMap<Integer, String>> implements Serializable, PassByValue {
}
