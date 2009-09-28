package com.siyeh.igtest.abstraction;

public enum X implements Y {
    ONE(0x101, "One"),
    TWO(0x102, "Two"),
    THREE(0x103, "Three");

    private int value;

    private String description;

    X(int value, String description) {
        this.value = value;
        this.description = description;
    }

    public int getValue() {
        return value;
    }

    public String toString() {
        return description;
    }

}