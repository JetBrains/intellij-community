import javax.swing.*
import groovy.swing.SwingBuilder

SwingBuilder builder = new SwingBuilder()

builder.frame(defaultCloseOperation : 0)

JFrame frame = new JFrame()
frame.<caret>defaultCloseOperation = 1