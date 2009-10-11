/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.TreeMap;

public class GeometryUtil implements SwingConstants {
  private static final int myArrowSize = 9;
  private static final Shape myArrowPolygon = new Polygon(new int[] {0, myArrowSize, 0, 0}, new int[] {0, myArrowSize /2, myArrowSize, 0}, 4);

  public static Point getIntersectionPoint(Line2D aSegment, Rectangle aRectangle) {

    if (segmentOutsideRectangle(aRectangle, aSegment)) {
      throw new IllegalArgumentException("Segment " + toString(aSegment) + " lies out of rectangle " + aRectangle + " or intersects more than one bound");
    }

    if (segmentInsideRectangle(aRectangle, aSegment)) {
      return null;
    }

    Line2D[] bounds = new Line2D[4];
    bounds[0] = getTopOf(aRectangle);
    bounds[1] = getRightOf(aRectangle);
    bounds[2] = getBottomOf(aRectangle);
    bounds[3] = getLeftOf(aRectangle);

    for (int i = 0; i < bounds.length; i++) {
      if (bounds[i].intersectsLine(aSegment)) {
        return getIntersectionPoint(aSegment, bounds[i]);
      }
    }

    return null;
  }

  public static Line2D.Double getLeftOf(Rectangle aRectangle) {
    return new Line2D.Double(aRectangle.getX(), aRectangle.getY(), aRectangle.getX(), aRectangle.getY() + aRectangle.getHeight());
  }

  public static Line2D.Double getBottomOf(Rectangle aRectangle) {
    return new Line2D.Double(aRectangle.getX(), aRectangle.getY() + aRectangle.getHeight(), aRectangle.getX() + aRectangle.getWidth(), aRectangle.getY() + aRectangle.getHeight());
  }

  public static Line2D.Double getRightOf(Rectangle aRectangle) {
    return new Line2D.Double(aRectangle.getX() + aRectangle.getWidth(), aRectangle.getY(), aRectangle.getX() + aRectangle.getWidth(), aRectangle.getY() + aRectangle.getHeight());
  }

  public static Line2D.Double getTopOf(Rectangle aRectangle) {
    return new Line2D.Double(aRectangle.getX(), aRectangle.getY(), aRectangle.getX() + aRectangle.getWidth(), aRectangle.getY());
  }


  private static boolean segmentInsideRectangle(Rectangle aRectangle, Line2D aSegment) {
    return isWithin(aRectangle, aSegment.getP1()) && isWithin(aRectangle, aSegment.getP2());
  }

  private static boolean segmentOutsideRectangle(Rectangle aRectangle, Line2D aSegment) {
    return (!isWithin(aRectangle, aSegment.getP1())) && (!isWithin(aRectangle, aSegment.getP2()));
  }

  public static boolean isWithin(Rectangle aRectangle, Point2D aPoint) {
    return
        (aPoint.getX() > aRectangle.getX()) && (aPoint.getX() < aRectangle.getX() + aRectangle.getBounds().width)
        &&
        ((aPoint.getY() > aRectangle.getY()) && (aPoint.getY() < aRectangle.getY() + aRectangle.getBounds().height));
  }

  public static Point getIntersectionPoint(Line2D aFirst, Line2D aSecond) {
    double firstDeltaX = aFirst.getX2() - aFirst.getX1();
    double firstDeltaY = aFirst.getY2() - aFirst.getY1();

    double kFirst = firstDeltaY / firstDeltaX;
    double bFirst = aFirst.getY1() - kFirst * aFirst.getX1();


    double secondDeltaX = aSecond.getX2() - aSecond.getX1();
    double secondDeltaY = aSecond.getY2() - aSecond.getY1();

    double kSecond = secondDeltaY / secondDeltaX;
    double bSecond = aSecond.getY1() - kSecond * aSecond.getX1();


    double xIntersection = -100000000;
    double yIntersection = -100000000;


    double deltaK = (kFirst - kSecond);


    if (linesAreAngledAndParallel(kFirst, kSecond)) {
      return null;
    }

    if (Double.isInfinite(deltaK) || (0 == deltaK)) {

      if (firstDeltaX == secondDeltaX && 0 == firstDeltaX) {
        return null;
      }

      if (firstDeltaY == secondDeltaY && 0 == firstDeltaY) {
        return null;
      }

      if ((0 == firstDeltaX) && (0 == secondDeltaY)) {
        xIntersection = aFirst.getX1();
        yIntersection = aSecond.getY1();
      }
      else if ((0 == secondDeltaX) && (0 == firstDeltaY)) {
        xIntersection = aSecond.getX1();
        yIntersection = aFirst.getY1();
      }
      else {

        if (0 == firstDeltaX) {
          xIntersection = aFirst.getX1();
          yIntersection = kSecond * xIntersection + bSecond;
        }
        else {
          xIntersection = aSecond.getX1();
          yIntersection = kFirst * xIntersection + bFirst;
        }
      }


    }
    else {
      xIntersection = (bSecond - bFirst) / deltaK;
      yIntersection = kFirst * xIntersection + bFirst;
    }

    return new Point((int) xIntersection, (int) yIntersection);
  }

  private static boolean linesAreAngledAndParallel(double aKFirst, double aKSecond) {
    return (aKFirst == aKSecond) && (0 != aKFirst);
  }

  public static String toString(Line2D aLine) {
    return aLine.getP1() + ":" + aLine.getP2();
  }

  public static boolean intersects(Rectangle aRectangle, Line2D aLine) {
    if (aLine == null || aRectangle == null) {
      return false;
    }

    return (!segmentOutsideRectangle(aRectangle, aLine)) && (!segmentInsideRectangle(aRectangle, aLine));
  }

  public static int getPointPositionOnRectangle(Rectangle aRectangle, Point aPoint, int aEpsilon) {
    final int ERROR_CODE = Integer.MIN_VALUE;

    if (pointOnBound(getTopOf(aRectangle), aPoint, aEpsilon)) {
      return TOP;
    }
    else if (pointOnBound(getBottomOf(aRectangle), aPoint, aEpsilon)) {
      return BOTTOM;
    }
    else if (pointOnBound(getLeftOf(aRectangle), aPoint, aEpsilon)) {
      return LEFT;
    }
    else if (pointOnBound(getRightOf(aRectangle), aPoint, aEpsilon)) {
      return RIGHT;
    }
    else {
      return ERROR_CODE;
    }
  }

  private static boolean pointOnBound(Line2D aTop, Point aPoint, int aEpsilon) {
    return withinRange(aTop.getX1(), aTop.getX2(), aPoint.getX(), aEpsilon) && withinRange(aTop.getY1(), aTop.getY2(), aPoint.getY(), aEpsilon);
  }

  private static boolean withinRange(double aLeft, double aRight, double aValue, int aEpsilon) {
    return ((aLeft - aEpsilon) <= aValue) && ((aRight + aEpsilon) >= aValue);
  }

//  public static Point shiftByY(Line2D aLine, Point aPoint, int aPointDeltaY) {
//    return new Point((int) (aPoint.getX() + getShiftByY(aLine, aPointDeltaY)), (int) (aPoint.getY() + aPointDeltaY));
//  }
//
//  public static Point shiftByX(Line2D aLine, Point aPoint, int aPointDeltaX) {
//    return new Point((int) (aPoint.getX() + aPointDeltaX), (int) (aPoint.getY() + getShiftByX(aLine, aPointDeltaX)));
//  }

  public static double getShiftByY(Line2D aLine, double aPointDeltaY) {
    return aPointDeltaY * ((aLine.getX2() - aLine.getX1()) / (aLine.getY2() - aLine.getY1()));
  }

  public static double getShiftByX(Line2D aLine, double aPointDeltaX) {
    double width = aLine.getX2() - aLine.getX1();
    double height = aLine.getY2() - aLine.getY1();
    return aPointDeltaX * (height / width);
  }

  public static Shape getArrowShape(Line2D line, Point2D intersectionPoint) {
    final double deltaY = line.getP2().getY() - line.getP1().getY();
    final double length = Math.sqrt(Math.pow(deltaY, 2) + Math.pow(line.getP2().getX() - line.getP1().getX(), 2));

    double theta = Math.asin(deltaY / length);

    if (line.getP1().getX() > line.getP2().getX()) {
      theta = Math.PI - theta;
    }

    AffineTransform rotate = AffineTransform.getRotateInstance(theta, myArrowSize, myArrowSize / 2);
    Shape polygon = rotate.createTransformedShape(myArrowPolygon);

    AffineTransform move = AffineTransform.getTranslateInstance(intersectionPoint.getX() - myArrowSize, intersectionPoint.getY() - myArrowSize /2);
    polygon = move.createTransformedShape(polygon);
    return polygon;
  }

  private static class OrientedPoint extends Point {
    private final int myOrientation;

    public OrientedPoint(double x, double y, int aOrientation) {
      super((int) x, (int) y);
      myOrientation = aOrientation;
    }

    public int getOrientation() {
      return myOrientation;
    }
  }

  public static int getClosestToLineRectangleCorner(Rectangle aRectange, Line2D aSegment) {
    Point northWest = new OrientedPoint(aRectange.getX(), aRectange.getY(), NORTH_WEST);
    Point northEast = new OrientedPoint(aRectange.getMaxX(), aRectange.getY(), NORTH_EAST);
    Point southEast = new OrientedPoint(aRectange.getMaxX(), aRectange.getMaxY(), SOUTH_EAST);
    Point southWest = new OrientedPoint(aRectange.getX(), aRectange.getMaxY(), SOUTH_WEST);

    TreeMap sorter = new TreeMap();

    sorter.put(getDistance(aSegment, northWest), northWest);
    sorter.put(getDistance(aSegment, southWest), southWest);
    sorter.put(getDistance(aSegment, southEast), southEast);
    sorter.put(getDistance(aSegment, northEast), northEast);

    return ((OrientedPoint) sorter.get(sorter.firstKey())).getOrientation();

  }

  private static Double getDistance(Line2D aSegment, Point aPoint) {
    double lenght1 = getLineLength(aSegment.getX1(), aSegment.getY1(), aPoint.getX(), aPoint.getY());
    double lenght2 = getLineLength(aSegment.getX2(), aSegment.getY2(), aPoint.getX(), aPoint.getY());

    return new Double(lenght1 + lenght2);
  }

  public static double getLineLength(double aX1, double aY1, double aX2, double aY2) {
    double deltaX = aX2 - aX1;
    double deltaY = aY2 - aY1;

    return Math.sqrt(deltaX * deltaX + deltaY * deltaY);
  }

  public static double cos(Line2D aLine) {
    final double length = getLineLength(aLine.getX1(), aLine.getY1(), aLine.getX2(), aLine.getY2());
    if (length == 0) {
      throw new IllegalArgumentException(toString(aLine) + " has a zero length");
    }

    double deltaX = aLine.getX2() - aLine.getX1();
    return deltaX / length;
  }

  public static double sin(Line2D aLine) {
    final double length = getLineLength(aLine.getX1(), aLine.getY1(), aLine.getX2(), aLine.getY2());
    if (length == 0) {
      throw new IllegalArgumentException(toString(aLine) + " has a zero length");
    }

    double deltaY = aLine.getY2() - aLine.getY1();
    return deltaY / length;
  }

}
