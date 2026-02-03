class Example {
  String message

  static Example createDefault() {
    Example entity = new Example()

    entity.with {
      print mes<ref>sage // "NOT recognized"
    }

    return entity
  }
}