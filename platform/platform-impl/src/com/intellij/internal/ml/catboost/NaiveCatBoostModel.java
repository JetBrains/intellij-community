// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ml.catboost;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.function.Function;

public final class NaiveCatBoostModel {
  private final int[] floatFeaturesIndex;
  private final int floatFeatureCount;
  private final int binaryFeatureCount;
  private final int treeCount;
  private final ArrayList<double[]> floatFeatureBorders;
  private final int[] treeDepth;
  private final int[] treeSplitBorder;
  private final int[] treeSplitFeatureIndex;
  private final int[] treeSplitXorMask;
  private final double[] leafValues;
  private final double scale;
  private final double bias;
  private final boolean predictProbability;

  private NaiveCatBoostModel(int[] floatFeaturesIndex,
                             int floatFeatureCount,
                             int binaryFeatureCount,
                             int treeCount,
                             ArrayList<double[]> borders,
                             int[] treeDepth,
                             int[] treeSplitBorder,
                             int[] treeSplitFeatureIndex,
                             int[] treeSplitXorMask, double[] values, double scale, double bias,
                             boolean predictProbability) {
    this.floatFeaturesIndex = floatFeaturesIndex;
    this.floatFeatureCount = floatFeatureCount;
    this.binaryFeatureCount = binaryFeatureCount;
    this.treeCount = treeCount;
    floatFeatureBorders = borders;
    this.treeDepth = treeDepth;
    this.treeSplitBorder = treeSplitBorder;
    this.treeSplitFeatureIndex = treeSplitFeatureIndex;
    this.treeSplitXorMask = treeSplitXorMask;
    leafValues = values;
    this.scale = scale;
    this.bias = bias;
    this.predictProbability = predictProbability;
  }

  public double makePredict(double[] features) {
    assert features.length >= floatFeatureCount;

    // Binarise features
    int[] binaryFeatures = new int[binaryFeatureCount];

    for (int i = 0; i < binaryFeatureCount; i++) {
      double[] borders = floatFeatureBorders.get(i);
      for (double border : borders) {
        int index = floatFeaturesIndex[i];
        binaryFeatures[i] += (features[index] > border) ? 1 : 0;
      }
    }

    // Extract and sum values from trees
    double result = 0.;
    int treeSplitsIndex = 0;
    int currentTreeLeafValuesIndex = 0;

    for (int treeId = 0; treeId < treeCount; treeId++) {
      int current_tree_depth = treeDepth[treeId];
      int index = 0;
      for (int depth = 0; depth < current_tree_depth; depth++) {
        int border_val = treeSplitBorder[treeSplitsIndex + depth];
        int feature_index = treeSplitFeatureIndex[treeSplitsIndex + depth];
        int xor_mask = treeSplitXorMask[treeSplitsIndex + depth];
        int res = ((binaryFeatures[feature_index] ^ xor_mask) >= border_val) ? 1 : 0;
        index |= res << depth;
      }
      result += leafValues[currentTreeLeafValuesIndex + index];
      treeSplitsIndex += current_tree_depth;
      currentTreeLeafValuesIndex += (1 << current_tree_depth);
    }
    result = scale * result + bias;

    return predictProbability ? sigmoid(result) : result;
  }

  private static double sigmoid(double x) {
    return 1. / (1. + Math.exp(-x));
  }

  public static NaiveCatBoostModel loadModel(InputStream fileStream) throws IOException {
    DataInputStream stream = new DataInputStream(new BufferedInputStream(fileStream));
    int[] floatFeaturesIndex = readIntArray(stream);
    int floatFeatureCount = stream.readInt();
    int binaryFeatureCount = stream.readInt();
    int treeCount = stream.readInt();
    ArrayList<double[]> floatFeatureBorders = readArrayList(stream, (s) -> {
      try {
        return readDoubleArray(s);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    int[] treeDepth = readIntArray(stream);
    int[] treeSplitBorder = readIntArray(stream);
    int[] treeSplitFeatureIndex = readIntArray(stream);
    int[] treeSplitXorMask = readIntArray(stream);
    double[] leafValues = readDoubleArray(stream);
    double scale = stream.readDouble();
    double bias = stream.readDouble();

    boolean predictProbability = stream.available() > 0 && stream.readBoolean(); // check eof for backward compatibility

    return new NaiveCatBoostModel(
      floatFeaturesIndex,
      floatFeatureCount,
      binaryFeatureCount,
      treeCount,
      floatFeatureBorders,
      treeDepth,
      treeSplitBorder,
      treeSplitFeatureIndex,
      treeSplitXorMask,
      leafValues,
      scale,
      bias,
      predictProbability
    );
  }

  private static int[] readIntArray(DataInputStream stream) throws IOException {
    int length = stream.readInt();
    assert length > 0;
    byte[] bytes = new byte[length * Integer.BYTES];
    int[] array = new int[length];
    stream.readFully(bytes);
    IntBuffer buffer = ByteBuffer.wrap(bytes)
      .order(ByteOrder.BIG_ENDIAN)
      .asIntBuffer();
    buffer.get(array);
    return array;
  }

  private static double[] readDoubleArray(DataInputStream stream) throws IOException {
    int length = stream.readInt();
    assert length > 0;
    byte[] bytes = new byte[length * Double.BYTES];
    double[] array = new double[length];
    stream.readFully(bytes);
    DoubleBuffer buffer = ByteBuffer.wrap(bytes)
      .order(ByteOrder.BIG_ENDIAN)
      .asDoubleBuffer();
    buffer.get(array);
    return array;
  }

  private static <T> ArrayList<T> readArrayList(DataInputStream stream, Function<? super DataInputStream, ? extends T> readElement)
    throws IOException {
    int size = stream.readInt();
    assert size > 0;
    ArrayList<T> list = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      T element = readElement.apply(stream);
      list.add(element);
    }
    return list;
  }
}