import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Static landing site. Build output goes to `dist/`.
export default defineConfig({
  plugins: [react()],
})
