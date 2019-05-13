class LambdaBody {
  Comparator<String> comparator = Comparator.comparing(s -> (<caret>s.substring(2).isEmpty()));
}