package com.intellij.driver.model;

import com.intellij.driver.model.transport.PassByValue;

import java.io.Serializable;
import java.util.ArrayList;

public final class TextDataList extends ArrayList<TextData> implements Serializable, PassByValue {
}