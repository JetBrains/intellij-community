package com.intellij.driver.model;

import com.intellij.driver.model.transport.PassByValue;

import java.io.Serializable;
import java.util.ArrayList;

public class StringList extends ArrayList<String> implements Serializable, PassByValue {
}
