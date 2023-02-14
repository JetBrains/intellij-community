package com.intellij.ui.charts;

import com.intellij.util.Consumer;
import org.junit.Assert;
import org.junit.Test;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LineChartJavaTest {

  @Test
  public void createCategoryDatasetTest() {
    CategoryLineDataset<Double> dataset = LineDataset.of(-1.0, 0.0, 1.0, 2.0, 3.0);

    Iterable<Coordinates<Integer, Double>> data = dataset.getData();
    Iterator<Coordinates<Integer, Double>> iterator = data.iterator();

    Assert.assertTrue(data instanceof ArrayList);
    Assert.assertEquals(Coordinates.of(0, -1.0), iterator.next());
    Assert.assertEquals(Coordinates.of(1, 0.0), iterator.next());
    Assert.assertEquals(Coordinates.of(2, 1.0), iterator.next());
    Assert.assertEquals(Coordinates.of(3, 2.0), iterator.next());
    Assert.assertEquals(Coordinates.of(4, 3.0), iterator.next());
    Assert.assertFalse(iterator.hasNext());
  }

  @Test
  public void createXYDatasetTest() {
    Integer[] xs = {-1, 0, 1};
    Double[] ys = {-100.0, 0.0, 100.0};
    XYLineDataset<Integer, Double> dataset = LineDataset.of(xs, ys);

    Iterable<Coordinates<Integer, Double>> data = dataset.getData();
    Iterator<Coordinates<Integer, Double>> iterator = data.iterator();

    Assert.assertTrue(data instanceof ArrayList);
    Assert.assertEquals(Coordinates.of(-1, -100.0), iterator.next());
    Assert.assertEquals(Coordinates.of(0, 0.0), iterator.next());
    Assert.assertEquals(Coordinates.of(1, 100.0), iterator.next());
    Assert.assertFalse(iterator.hasNext());
  }

  @Test
  public void fastCreateLineChartTest() {
    CategoryLineChart<Double> chart = LineChart.of(1.0, 2.0, 3.0, 4.0, 5.0);
    CategoryLineDataset<Double> negativeValues = LineDataset.of(-1.0, -2.0, -3.0, -4.0, -5.0);
    chart.getDatasets().add(negativeValues);
    MinMax<Integer, Double> xy = chart.findMinMax();

    Assert.assertEquals(2, chart.getDatasets().size());
    Assert.assertTrue(chart.getComponent() instanceof JComponent);

    Assert.assertEquals(Integer.valueOf(0), xy.xMin);
    Assert.assertEquals(Integer.valueOf(4), xy.xMax);
    Assert.assertEquals(Double.valueOf(-5.0), xy.yMin);
    Assert.assertEquals(Double.valueOf(5.0), xy.yMax);
  }

  @Test
  public void gridLineChartTest() {
    Double[] doubles = {1.0, 2.0, 3.0, 4.0, 5.0};
    CategoryLineChart<Double> chart = LineChart.of(doubles);
    AtomicInteger counter = new AtomicInteger();

    final Consumer<GridLine<Integer, Double, ?>> common = gridline -> {
      MinMax<Integer, Double> xy = gridline.getXY();
      Assert.assertEquals(Integer.valueOf(0), xy.xMin);
      Assert.assertEquals(Integer.valueOf(4), xy.xMax);
      Assert.assertEquals(Double.valueOf(1.0), xy.yMin);
      Assert.assertEquals(Double.valueOf(5.0), xy.yMax);

      // mutations:
      xy.setXMin(-1);
      xy.setXMax(60000);
      xy.setYMin(-1.0);
      xy.setYMax(-60000.0);

      Assert.assertEquals(doubles[counter.getAndIncrement()], gridline.getValue());
    };

    chart.getGrid().setXPainter(gridline -> {
      Assert.assertEquals(Integer.valueOf(counter.getAndIncrement()), gridline.getValue());
      common.consume(gridline);
    });
    counter.set(0);
    chart.getGrid().setYPainter(gridline -> {
      Assert.assertEquals(doubles[counter.getAndIncrement()], gridline.getValue());
      common.consume(gridline);
    });
  }

  @Test
  public void dynamicUpdateTest() {
    XYLineDataset<Double, Double> maxValuesDataset = new XYLineDataset<>();
    maxValuesDataset.setLabel("MAX VALUES");

    XYLineDataset<Double, Double> minValuesDataset = new XYLineDataset<>();
    minValuesDataset.setLabel("MIN VALUES");

    XYLineChart<Double, Double> chart = LineChart.of(maxValuesDataset, minValuesDataset);

    Assert.assertFalse(chart.getDataset("MAX VALUES").getData().iterator().hasNext());
    Assert.assertFalse(chart.getDataset("MIN VALUES").getData().iterator().hasNext());

    for (int i = 1; i < 1000; i++) {
      chart.getDataset("MAX VALUES").add(Coordinates.of( Double.valueOf(i), Double.valueOf(i)));
      Assert.assertEquals(i, ((List<?>) chart.getDataset("MAX VALUES").getData()).size());
      Assert.assertFalse(chart.getDataset("MIN VALUES").getData().iterator().hasNext());
    }
  }

  @Test
  public void customGeneratorDataset() {
    XYLineChart<Integer, Double> chart = LineChart.of(new XYLineDataset<>());
    ChartUtils.generator(45).prepare(-360, 360).forEach(value -> {
      chart.getDataset().add(Coordinates.of(value, Math.sin(Math.toRadians(value))));
    });

    MinMax<Integer, Double> xy = chart.findMinMax();
    Assert.assertEquals(Integer.valueOf(-360), xy.xMin);
    Assert.assertEquals(Integer.valueOf(360), xy.xMax);

    Iterator<Coordinates<Integer, Double>> iterator = chart.getDataset().getData().iterator();
    for (int i = -360; i <= 360; i += 45) {
      Coordinates<Integer, Double> next = iterator.next();
      Assert.assertEquals(Integer.valueOf(i), next.getX());
      Assert.assertEquals(Double.valueOf(Math.sin(Math.toRadians(i))), next.getY());
    }
    Assert.assertFalse(iterator.hasNext());
  }
}
