package pkg;

public class TestFloatLiterals {
  public static final double MAX_D = Double.MAX_VALUE;
  public static final double MIN_D = Double.MIN_VALUE;
  public static final double MIN_ND = Double.MIN_NORMAL;
  public static final float MAX_F = Float.MAX_VALUE;
  public static final float MIN_F = Float.MIN_VALUE;
  public static final float MINN_F = Float.MIN_NORMAL;
  public static final double MAX_FD = Float.MAX_VALUE;
  public static final double MIN_FD = Float.MIN_VALUE;
  public static final double MINN_FD = Float.MIN_NORMAL;

  public static final double NEG_MAX_D = -Double.MAX_VALUE;
  public static final double NEG_MIN_D = -Double.MIN_VALUE;
  public static final double NEG_MIN_ND = -Double.MIN_NORMAL;
  public static final float NEG_MAX_F = -Float.MAX_VALUE;
  public static final float NEG_MIN_F = -Float.MIN_VALUE;
  public static final float NEG_MINN_F = -Float.MIN_NORMAL;
  public static final double NEG_MAX_FD = -Float.MAX_VALUE;
  public static final double NEG_MIN_FD = -Float.MIN_VALUE;
  public static final double NEG_MINN_FD = -Float.MIN_NORMAL;

  public static final double PI_D = Math.PI;
  public static final double E_D = Math.E;
  public static final float PI_F = (float)Math.PI;
  public static final float E_F = (float)Math.E;
  public static final double PI_FD = PI_F;
  public static final double E_FD = E_F;

  public static final double ONE_D = 1.0;
  public static final float ONE_F = 1.0F;
  public static final double ONE_FD = 1.0F;

  public static final double ONETENTH_D = .1;
  public static final float ONETENTH_F = .1F;
  public static final double ONETENTH_FD = .1F;

  public static final double FRACTION_D = 42D / 188D;
  public static final float FRACTION_F = 42F / 188F;
  public static final double FRACTION_FD = 42F / 188F;

  public static final double DEG_TO_RAD_D = PI_D / 180D;
  public static final float DEG_TO_RAD_F = PI_F / 180F;

  public static double degreesToRadians(double in) {
    return in * DEG_TO_RAD_D;
  }

  public static float degreesToRadians(float in) {
    return in * DEG_TO_RAD_F;
  }

  public static double degreesToRadiansLossy(double in) {
    return in * DEG_TO_RAD_F;
  }
}